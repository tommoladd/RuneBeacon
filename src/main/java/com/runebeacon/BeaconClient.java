package com.runebeacon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.runebeacon.model.Group;
import com.runebeacon.model.Invite;
import com.runebeacon.model.Permission;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Talks to the RuneBeacon server: registration, group REST calls and the live WebSocket.
 * All callbacks and store mutations are delivered on the Swing EDT.
 */
@Slf4j
@Singleton
public class BeaconClient
{
	public enum Status
	{
		DISABLED("Disabled in settings"),
		NO_SERVER("Invalid dev server URL"),
		WAITING_FOR_LOGIN("Log in to connect"),
		CONNECTING("Connecting..."),
		CONNECTED("Connected"),
		RECONNECTING("Reconnecting..."),
		REPLACED("Connected from another client"),
		ERROR("Error");

		@Getter
		private final String text;

		Status(String text)
		{
			this.text = text;
		}
	}

	public interface StatusListener
	{
		void statusChanged(Status status, String detail);
	}

	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String TOKEN_KEY_PREFIX = "token_";
	private static final int CLOSE_REPLACED = 4001;
	private static final long PING_SECONDS = 30;
	private static final long MAX_BACKOFF_SECONDS = 60;

	private final OkHttpClient http;
	private final Gson gson;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;
	private final GroupStore store;
	private final RuneBeaconConfig config;

	private StatusListener statusListener;
	private Consumer<String> onMemberOnline;
	private Consumer<Invite> onInviteReceived;
	private Consumer<TilePingEvent> onTilePing;
	private Consumer<ItemPingEvent> onItemPing;

	// Connection state. Guarded by "this".
	private HttpUrl baseUrl;
	private long accountHash = -1;
	private String token;
	private WebSocket socket;
	private String rsn;
	private volatile int generation;
	private boolean wantConnected;
	/** Stopped after a permanent error; don't retry until retry() or the account/url changes. */
	private boolean halted;
	private int backoffSeconds = 2;
	private ScheduledFuture<?> reconnectFuture;
	private ScheduledFuture<?> pingFuture;
	@Getter
	private volatile Status status = Status.DISABLED;
	@Getter
	private volatile String statusDetail;
	@Getter
	private volatile boolean socketOpen;

	@Inject
	BeaconClient(OkHttpClient http, Gson gson, ConfigManager configManager, ScheduledExecutorService executor,
		GroupStore store, RuneBeaconConfig config, @Named("developerMode") boolean developerMode)
	{
		this.http = http;
		this.gson = gson;
		this.configManager = configManager;
		this.executor = executor;
		this.store = store;
		this.config = config;
		this.developerMode = developerMode;
	}

	/** True when RuneLite runs with --developer-mode (e.g. ./gradlew run). */
	@Getter
	private final boolean developerMode;

	/**
	 * The server to use: always the built-in public server, unless running in developer mode with
	 * an override set.
	 */
	/** Developer mode only: set (or clear, with blank) the server override. Reconnects via ConfigChanged. */
	public void setDevServerOverride(String url)
	{
		if (!developerMode)
		{
			return;
		}
		String v = url == null ? "" : url.trim();
		if (v.isEmpty())
		{
			configManager.unsetConfiguration(RuneBeaconConfig.GROUP, "serverUrl");
		}
		else
		{
			configManager.setConfiguration(RuneBeaconConfig.GROUP, "serverUrl", v);
		}
	}

	public String effectiveServerUrl()
	{
		String override = config.serverUrl();
		if (developerMode && override != null && !override.trim().isEmpty())
		{
			return override.trim();
		}
		return RuneBeaconConfig.DEFAULT_SERVER_URL;
	}

	void setStatusListener(StatusListener l)
	{
		this.statusListener = l;
	}

	void setOnMemberOnline(Consumer<String> fn)
	{
		this.onMemberOnline = fn;
	}

	void setOnInviteReceived(Consumer<Invite> fn)
	{
		this.onInviteReceived = fn;
	}

	void setOnTilePing(Consumer<TilePingEvent> fn)
	{
		this.onTilePing = fn;
	}

	void setOnItemPing(Consumer<ItemPingEvent> fn)
	{
		this.onItemPing = fn;
	}

	// ------------------------------------------------------------------ lifecycle

	/**
	 * Ensure we're connected as the given account. Safe to call repeatedly (e.g. every tick);
	 * it only acts when something changed.
	 */
	synchronized void connect(long accountHash, String rsn)
	{
		HttpUrl url = parseServerUrl(effectiveServerUrl());
		if (url == null)
		{
			disconnect(Status.NO_SERVER);
			return;
		}
		if ((wantConnected || halted) && accountHash == this.accountHash && url.equals(baseUrl))
		{
			return;
		}
		if (wantConnected || halted)
		{
			disconnect(Status.CONNECTING);
		}

		this.baseUrl = url;
		this.accountHash = accountHash;
		this.rsn = rsn;
		this.wantConnected = true;
		this.halted = false;
		this.backoffSeconds = 2;
		this.token = configManager.getConfiguration(RuneBeaconConfig.GROUP, tokenKey());
		setStatus(Status.CONNECTING, null);
		register();
	}

	synchronized void disconnect(Status newStatus)
	{
		wantConnected = false;
		halted = false;
		generation++;
		cancel(reconnectFuture);
		cancel(pingFuture);
		reconnectFuture = null;
		pingFuture = null;
		if (socket != null)
		{
			socket.close(1000, "bye");
			socket = null;
		}
		socketOpen = false;
		accountHash = -1;
		token = null;
		SwingUtilities.invokeLater(store::clear);
		setStatus(newStatus, null);
	}

	/** Must hold the lock. */
	private void halt()
	{
		wantConnected = false;
		halted = true;
		socketOpen = false;
		cancel(pingFuture);
		cancel(reconnectFuture);
	}

	/** Clear a halted state so the next connect() call tries again. */
	synchronized void retry()
	{
		if (halted)
		{
			disconnect(Status.CONNECTING);
		}
	}

	/** Send a state patch. Dropped silently if not connected; the collector resends on reconnect. */
	@lombok.Value
	static class TilePingEvent
	{
		String userId;
		String groupId;
		int x;
		int y;
		int plane;
		Integer world;
	}

	@lombok.Value
	static class ItemPingEvent
	{
		String userId;
		String groupId;
		String targetUserId;
		int slot;
		int itemId;
		Integer world;
	}

	/** Ping an inventory slot of a group member (possibly yourself). Returns false if not connected. */
	public boolean sendItemPing(String groupId, String targetUserId, int slot, int itemId)
	{
		WebSocket ws;
		synchronized (this)
		{
			ws = socketOpen ? socket : null;
		}
		if (ws == null || groupId == null)
		{
			return false;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "item_ping");
		msg.addProperty("g", groupId);
		msg.addProperty("target", targetUserId);
		msg.addProperty("slot", slot);
		msg.addProperty("item", itemId);
		return ws.send(gson.toJson(msg));
	}

	/** Ping a tile for one group. Returns false if not connected. */
	boolean sendTilePing(String groupId, int x, int y, int plane)
	{
		WebSocket ws;
		synchronized (this)
		{
			ws = socketOpen ? socket : null;
		}
		if (ws == null || groupId == null)
		{
			return false;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "tile_ping");
		msg.addProperty("g", groupId);
		msg.addProperty("x", x);
		msg.addProperty("y", y);
		msg.addProperty("plane", plane);
		return ws.send(gson.toJson(msg));
	}

	boolean sendState(JsonObject patch)
	{
		WebSocket ws;
		synchronized (this)
		{
			ws = socketOpen ? socket : null;
		}
		if (ws == null)
		{
			return false;
		}
		JsonObject msg = new JsonObject();
		msg.addProperty("t", "state");
		msg.add("d", patch);
		return ws.send(gson.toJson(msg));
	}

	/**
	 * Config key for this account's login token on the current server. The public server keeps the
	 * original key; developer-mode servers get their own, so switching between them doesn't clobber
	 * tokens. Must hold the lock (reads baseUrl/accountHash).
	 */
	private String tokenKey()
	{
		String key = TOKEN_KEY_PREFIX + accountHash;
		if (baseUrl == null || baseUrl.equals(parseServerUrl(RuneBeaconConfig.DEFAULT_SERVER_URL)))
		{
			return key;
		}
		return key + "_" + (baseUrl.host() + "_" + baseUrl.port()).replaceAll("[^A-Za-z0-9_]", "_");
	}

	static HttpUrl parseServerUrl(String raw)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return null;
		}
		HttpUrl url = HttpUrl.parse(raw.trim());
		if (url == null)
		{
			return null;
		}
		// Strip any path the user pasted; the API lives at /v1.
		return url.newBuilder().encodedPath("/").query(null).fragment(null).build();
	}

	// ------------------------------------------------------------------ registration & socket

	/** Must hold the lock. */
	private void register()
	{
		final int gen = generation;
		JsonObject body = new JsonObject();
		body.addProperty("accountHash", Long.toString(accountHash));
		body.addProperty("rsn", rsn);

		Request.Builder rb = new Request.Builder()
			.url(baseUrl.newBuilder().addPathSegments("v1/register").build())
			.post(RequestBody.create(JSON, gson.toJson(body)));
		if (token != null)
		{
			rb.header("Authorization", "Bearer " + token);
		}

		http.newCall(rb.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("RuneBeacon register failed", e);
				scheduleReconnect(gen);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody rbody = response.body())
				{
					String text = rbody == null ? "" : rbody.string();
					synchronized (BeaconClient.this)
					{
						if (gen != generation)
						{
							return;
						}
						if (response.isSuccessful())
						{
							JsonObject json = new JsonParser().parse(text).getAsJsonObject();
							token = json.get("token").getAsString();
							configManager.setConfiguration(RuneBeaconConfig.GROUP, tokenKey(), token);
							openSocket();
						}
						else if (response.code() == 409)
						{
							halt();
							setStatus(Status.ERROR, "This account is already registered from another install. "
								+ "Copy the '" + tokenKey() + "' setting from that install, or sync your RuneLite profile.");
						}
						else if (response.code() >= 500 || response.code() == 429)
						{
							scheduleReconnect(gen);
						}
						else
						{
							halt();
							setStatus(Status.ERROR, errorMessage(text, response.code()));
						}
					}
				}
				catch (Exception e)
				{
					log.debug("RuneBeacon register response error", e);
					scheduleReconnect(gen);
				}
			}
		});
	}

	/** Must hold the lock. */
	private void openSocket()
	{
		final int gen = generation;
		HttpUrl wsUrl = baseUrl.newBuilder().addPathSegments("v1/ws").build();
		Request req = new Request.Builder()
			.url(wsUrl)
			.header("Authorization", "Bearer " + token)
			.build();
		socket = http.newWebSocket(req, new Listener(gen));
	}

	private synchronized void scheduleReconnect(int gen)
	{
		if (gen != generation || !wantConnected)
		{
			return;
		}
		socketOpen = false;
		cancel(pingFuture);
		setStatus(Status.RECONNECTING, null);
		final int delay = backoffSeconds;
		backoffSeconds = (int) Math.min(MAX_BACKOFF_SECONDS, backoffSeconds * 2L);
		cancel(reconnectFuture);
		reconnectFuture = executor.schedule(() ->
		{
			synchronized (BeaconClient.this)
			{
				if (gen != generation || !wantConnected)
				{
					return;
				}
				if (token == null)
				{
					register();
				}
				else
				{
					openSocket();
				}
			}
		}, delay, TimeUnit.SECONDS);
	}

	private final class Listener extends WebSocketListener
	{
		private final int gen;

		Listener(int gen)
		{
			this.gen = gen;
		}

		@Override
		public void onOpen(WebSocket webSocket, Response response)
		{
			synchronized (BeaconClient.this)
			{
				if (gen != generation)
				{
					webSocket.close(1000, "stale");
					return;
				}
				socketOpen = true;
				backoffSeconds = 2;
				cancel(pingFuture);
				pingFuture = executor.scheduleAtFixedRate(() -> webSocket.send("{\"t\":\"ping\"}"),
					PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
			}
			setStatus(Status.CONNECTED, null);
		}

		@Override
		public void onMessage(WebSocket webSocket, String text)
		{
			if (gen != generation)
			{
				return;
			}
			JsonObject msg;
			try
			{
				msg = new JsonParser().parse(text).getAsJsonObject();
			}
			catch (Exception e)
			{
				log.debug("RuneBeacon bad message: {}", text);
				return;
			}
			SwingUtilities.invokeLater(() -> handleMessage(gen, msg));
		}

		@Override
		public void onClosing(WebSocket webSocket, int code, String reason)
		{
			webSocket.close(1000, null);
		}

		@Override
		public void onClosed(WebSocket webSocket, int code, String reason)
		{
			if (code == CLOSE_REPLACED)
			{
				synchronized (BeaconClient.this)
				{
					if (gen == generation)
					{
						halt();
						setStatus(Status.REPLACED, null);
					}
				}
				return;
			}
			scheduleReconnect(gen);
		}

		@Override
		public void onFailure(WebSocket webSocket, Throwable t, Response response)
		{
			if (response != null && response.code() == 401)
			{
				synchronized (BeaconClient.this)
				{
					if (gen == generation)
					{
						halt();
						setStatus(Status.ERROR, "Server rejected this account's token. Remove the RuneBeacon '"
							+ tokenKey() + "' setting and log in again to re-register.");
					}
				}
				return;
			}
			log.debug("RuneBeacon socket failure", t);
			scheduleReconnect(gen);
		}
	}

	// ------------------------------------------------------------------ incoming messages (EDT)

	private void handleMessage(int gen, JsonObject msg)
	{
		if (gen != generation || !msg.has("t"))
		{
			return;
		}
		switch (msg.get("t").getAsString())
		{
			case "hello":
				store.setSelfUserId(msg.get("userId").getAsString());
				store.setGroups(gson.fromJson(msg.get("groups"), new TypeToken<List<Group>>(){}.getType()));
				store.setInvites(msg.has("invites")
					? gson.fromJson(msg.get("invites"), new TypeToken<List<Invite>>(){}.getType())
					: null);
				break;
			case "invite_received":
			{
				Invite invite = gson.fromJson(msg.get("invite"), Invite.class);
				store.upsertInvite(invite);
				if (onInviteReceived != null)
				{
					onInviteReceived.accept(invite);
				}
				break;
			}
			case "tile_ping":
				if (onTilePing != null)
				{
					JsonElement world = msg.get("world");
					onTilePing.accept(new TilePingEvent(
						msg.get("u").getAsString(),
						msg.get("g").getAsString(),
						msg.get("x").getAsInt(),
						msg.get("y").getAsInt(),
						msg.get("plane").getAsInt(),
						world == null || world.isJsonNull() ? null : world.getAsInt()));
				}
				break;
			case "item_ping":
				if (onItemPing != null)
				{
					JsonElement world = msg.get("world");
					onItemPing.accept(new ItemPingEvent(
						msg.get("u").getAsString(),
						msg.get("g").getAsString(),
						msg.get("target").getAsString(),
						msg.get("slot").getAsInt(),
						msg.get("item").getAsInt(),
						world == null || world.isJsonNull() ? null : world.getAsInt()));
				}
				break;
			case "invite_removed":
				store.removeInvite(msg.get("inviteId").getAsString());
				break;
			case "state":
				store.applyPatch(msg.get("u").getAsString(), msg.getAsJsonObject("d"));
				break;
			case "presence":
			{
				String userId = msg.get("u").getAsString();
				boolean online = msg.get("online").getAsBoolean();
				Long lastSeen = msg.has("lastSeen") && !msg.get("lastSeen").isJsonNull() ? msg.get("lastSeen").getAsLong() : null;
				if (store.setPresence(userId, online, lastSeen) && onMemberOnline != null)
				{
					onMemberOnline.accept(userId);
				}
				break;
			}
			case "group_updated":
				store.upsertGroup(gson.fromJson(msg.get("group"), Group.class));
				break;
			case "group_deleted":
				store.removeGroup(msg.get("groupId").getAsString());
				break;
			case "error":
				log.debug("RuneBeacon server error: {}", msg);
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ REST

	public void createGroup(String name, boolean temporary, int durationHours, Consumer<Group> ok, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("type", temporary ? "temporary" : "permanent");
		if (temporary)
		{
			body.addProperty("durationHours", durationHours);
		}
		call("POST", "v1/groups", body, Group.class, g ->
		{
			store.upsertGroup(g);
			ok.accept(g);
		}, err);
	}

	public void joinGroup(String inviteCode, Consumer<Group> ok, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.addProperty("inviteCode", inviteCode.trim().toUpperCase());
		call("POST", "v1/groups/join", body, Group.class, g ->
		{
			store.upsertGroup(g);
			ok.accept(g);
		}, err);
	}

	public void renameGroup(String groupId, String name, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		call("PATCH", "v1/groups/" + groupId, body, Group.class, store::upsertGroup, err);
	}

	public void leaveGroup(String groupId, Consumer<String> err)
	{
		call("POST", "v1/groups/" + groupId + "/leave", new JsonObject(), null, v -> store.removeGroup(groupId), err);
	}

	public void deleteGroup(String groupId, Consumer<String> err)
	{
		call("DELETE", "v1/groups/" + groupId, null, null, v -> store.removeGroup(groupId), err);
	}

	public void regenerateInvite(String groupId, Consumer<String> ok, Consumer<String> err)
	{
		call("POST", "v1/groups/" + groupId + "/invite", new JsonObject(), JsonObject.class,
			json -> ok.accept(json.get("inviteCode").getAsString()), err);
	}

	public void kick(String groupId, String userId, Consumer<String> err)
	{
		call("POST", "v1/groups/" + groupId + "/members/" + userId + "/kick", new JsonObject(), null, v -> {}, err);
	}

	public void setPermissions(String groupId, String userId, Collection<Permission> permissions, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.add("permissions", permissionArray(permissions));
		call("PUT", "v1/groups/" + groupId + "/members/" + userId + "/permissions", body, null, v -> {}, err);
	}

	public void setDefaultPermissions(String groupId, Collection<Permission> permissions, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.add("defaultPermissions", permissionArray(permissions));
		call("PATCH", "v1/groups/" + groupId, body, Group.class, store::upsertGroup, err);
	}

	public void inviteByName(String groupId, String rsn, Runnable ok, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);
		call("POST", "v1/groups/" + groupId + "/invites", body, null, v -> ok.run(), err);
	}

	public void acceptInvite(String inviteId, Consumer<Group> ok, Consumer<String> err)
	{
		call("POST", "v1/invites/" + inviteId + "/accept", new JsonObject(), Group.class, g ->
		{
			store.removeInvite(inviteId);
			store.upsertGroup(g);
			ok.accept(g);
		}, err);
	}

	public void declineInvite(String inviteId, Consumer<String> err)
	{
		call("POST", "v1/invites/" + inviteId + "/decline", new JsonObject(), null, v -> store.removeInvite(inviteId), err);
	}

	public void extendGroup(String groupId, int hours, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.addProperty("hours", hours);
		call("POST", "v1/groups/" + groupId + "/extend", body, Group.class, store::upsertGroup, err);
	}

	private static JsonArray permissionArray(Collection<Permission> permissions)
	{
		JsonArray arr = new JsonArray();
		for (Permission p : permissions)
		{
			arr.add(p.name());
		}
		return arr;
	}

	public void transferOwnership(String groupId, String userId, Consumer<String> err)
	{
		JsonObject body = new JsonObject();
		body.addProperty("userId", userId);
		call("POST", "v1/groups/" + groupId + "/transfer", body, null, v -> {}, err);
	}

	private <T> void call(String method, String path, JsonElement body, Class<T> type, Consumer<T> ok, Consumer<String> err)
	{
		HttpUrl base;
		String tok;
		synchronized (this)
		{
			base = baseUrl;
			tok = token;
		}
		if (base == null || tok == null || !socketOpen)
		{
			err.accept("Not connected to the RuneBeacon server");
			return;
		}
		Request req = new Request.Builder()
			.url(base.newBuilder().addPathSegments(path).build())
			.header("Authorization", "Bearer " + tok)
			.method(method, body == null ? null : RequestBody.create(JSON, gson.toJson(body)))
			.build();

		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				SwingUtilities.invokeLater(() -> err.accept("Couldn't reach the server: " + e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody rbody = response.body())
				{
					String text = rbody == null ? "" : rbody.string();
					if (!response.isSuccessful())
					{
						String message = errorMessage(text, response.code());
						SwingUtilities.invokeLater(() -> err.accept(message));
						return;
					}
					T result = type == null || text.isEmpty() ? null : gson.fromJson(text, type);
					SwingUtilities.invokeLater(() -> ok.accept(result));
				}
				catch (Exception e)
				{
					log.debug("RuneBeacon response error", e);
					SwingUtilities.invokeLater(() -> err.accept("Unexpected response from server"));
				}
			}
		});
	}

	private String errorMessage(String body, int code)
	{
		try
		{
			JsonObject json = new JsonParser().parse(body).getAsJsonObject();
			if (json.has("message"))
			{
				return json.get("message").getAsString();
			}
		}
		catch (Exception ignored)
		{
			// fall through
		}
		return "Server error (" + code + ")";
	}

	private void setStatus(Status s, String detail)
	{
		this.status = s;
		this.statusDetail = detail;
		StatusListener l = statusListener;
		if (l != null)
		{
			SwingUtilities.invokeLater(() -> l.statusChanged(s, detail));
		}
	}

	private static void cancel(ScheduledFuture<?> f)
	{
		if (f != null)
		{
			f.cancel(false);
		}
	}
}
