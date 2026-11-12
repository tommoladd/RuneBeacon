package com.runebeacon;

import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.runebeacon.model.Group;
import com.runebeacon.model.Invite;
import com.runebeacon.model.Member;
import com.runebeacon.ui.PanelServices;
import com.runebeacon.ui.PingSettings;
import com.runebeacon.ui.RuneBeaconPanel;
import com.runebeacon.ui.UiUtil;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.SoundEffectID;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "RuneBeacon",
	description = "Friend groups: see members' status, world, location, activity, gear and stats, with world map pins",
	tags = {"friends", "group", "party", "map", "world", "location", "panel"}
)
public class RuneBeaconPlugin extends Plugin implements GroupStore.Listener, BeaconClient.StatusListener
{
	private static final String SELECTED_GROUP_KEY = "selectedGroup";
	/** UI refresh cadence while member data is changing. */
	private static final int REFRESH_MS = 600;
	/** Slow refresh so "seen 5m ago" / "2h left" texts stay current. */
	private static final int CLOCK_REFRESH_TICKS = 50;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RuneBeaconConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PanelServices panelServices;

	@Inject
	private Notifier notifier;

	@Inject
	private BeaconClient beaconClient;

	@Inject
	private GroupStore store;

	@Inject
	private StateCollector collector;

	@Inject
	private MapPins mapPins;

	private RuneBeaconPanel panel;
	private NavigationButton navButton;
	private Timer refreshTimer;
	private volatile boolean dirty;
	/** Whether any group lets us invite; read on the client thread for the menu option. */
	private volatile boolean canInvite;
	/** Selected group, readable from the client thread (pings go to this group). */
	private volatile String selectedGroupId;
	/** True while the ping hotkey is held. */
	private volatile boolean pingHotkeyHeld;
	/** Client thread: whether we last told the panel we are appearing offline. */
	private boolean appearingOffline;
	private int clockCounter;

	@Inject
	private TilePingOverlay tilePingOverlay;

	@Inject
	private InventoryPingOverlay inventoryPingOverlay;

	@Inject
	private PingSettings pingSettings;

	/** Our current world, for the "pings from other worlds" filter. */
	private volatile Integer currentWorld;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	private final HotkeyListener pingHotkeyListener = new HotkeyListener(() -> config.pingHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			pingHotkeyHeld = true;
			pingSettings.setHotkeyHeld(true);
		}

		@Override
		public void hotkeyReleased()
		{
			pingHotkeyHeld = false;
			pingSettings.setHotkeyHeld(false);
		}
	};

	@Override
	protected void startUp() throws Exception
	{
		String savedGroup = configManager.getConfiguration(RuneBeaconConfig.GROUP, SELECTED_GROUP_KEY);
		panel = new RuneBeaconPanel(beaconClient, store, panelServices, savedGroup,
			this::onSelectedGroupChanged, this::retry);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("RuneBeacon")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		store.addListener(this);
		beaconClient.setStatusListener(this);
		beaconClient.setOnMemberOnline(this::memberCameOnline);
		beaconClient.setOnInviteReceived(this::inviteReceived);
		beaconClient.setOnTilePing(this::tilePingReceived);
		beaconClient.setOnItemPing(this::itemPingReceived);
		selectedGroupId = panel.getSelectedGroupId();
		overlayManager.add(tilePingOverlay);
		overlayManager.add(inventoryPingOverlay);
		keyManager.registerKeyListener(pingHotkeyListener);

		refreshTimer = new Timer(REFRESH_MS, e -> onRefreshTimer());
		refreshTimer.start();

		beaconClient.disconnect(config.enabled() ? BeaconClient.Status.WAITING_FOR_LOGIN : BeaconClient.Status.DISABLED);
		log.debug("RuneBeacon started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		beaconClient.disconnect(BeaconClient.Status.DISABLED);
		beaconClient.setStatusListener(null);
		beaconClient.setOnMemberOnline(null);
		beaconClient.setOnInviteReceived(null);
		beaconClient.setOnTilePing(null);
		beaconClient.setOnItemPing(null);
		keyManager.unregisterKeyListener(pingHotkeyListener);
		pingHotkeyHeld = false;
		pingSettings.setHotkeyHeld(false);
		pingSettings.setPvpPaused(false);
		overlayManager.remove(tilePingOverlay);
		tilePingOverlay.clear();
		overlayManager.remove(inventoryPingOverlay);
		inventoryPingOverlay.clear();
		store.removeListener(this);
		mapPins.clear();
		if (refreshTimer != null)
		{
			refreshTimer.stop();
			refreshTimer = null;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		log.debug("RuneBeacon stopped");
	}

	@Provides
	RuneBeaconConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneBeaconConfig.class);
	}

	// ------------------------------------------------------------------ game events (client thread)

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		currentWorld = client.getWorld();
		if (!config.enabled())
		{
			return;
		}
		Player local = client.getLocalPlayer();
		long hash = client.getAccountHash();
		if (local == null || local.getName() == null || hash == -1)
		{
			return;
		}
		beaconClient.connect(hash, local.getName());
		sendPatch();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOGGING_IN || state == GameState.HOPPING)
		{
			collector.resetTraining();
		}
		if (state == GameState.LOGGING_IN)
		{
			collector.resetPrivateChat();
		}
		// No GameTicks fire outside LOGGED_IN, so push state transitions directly.
		if (config.enabled() && state != GameState.LOGGED_IN)
		{
			sendPatch();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		collector.onStatChanged(e);
	}

	/**
	 * Adds "RuneBeacon invite" to player names in the friends list, clan/friends chat member lists
	 * and the chatbox — the same places and anchor options RuneLite's own hiscore "Lookup" uses.
	 * The option only opens our invite dialog; nothing is sent to the game server.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		if (!canInvite || !config.enabled() || !config.inviteMenuOption() || !beaconClient.isSocketOpen())
		{
			return;
		}
		if (e.getType() != MenuAction.CC_OP.getId() && e.getType() != MenuAction.CC_OP_LOW_PRIORITY.getId())
		{
			return;
		}
		String option = e.getOption();
		int component = e.getActionParam1();
		int iface = WidgetUtil.componentToInterface(component);
		boolean anchor;
		if (iface == InterfaceID.FRIENDS)
		{
			anchor = "Delete".equals(option);
		}
		else if (iface == InterfaceID.CHATBOX || iface == InterfaceID.PM_CHAT)
		{
			anchor = "Add ignore".equals(option) || "Message".equals(option);
		}
		else if (iface == InterfaceID.CHATCHANNEL_CURRENT
			|| component == InterfaceID.ClansSidepanel.PLAYERLIST
			|| component == InterfaceID.ClansGuestSidepanel.PLAYERLIST)
		{
			anchor = "Add ignore".equals(option) || "Remove friend".equals(option);
		}
		else
		{
			anchor = false;
		}
		if (!anchor)
		{
			return;
		}

		String rsn = Text.toJagexName(Text.removeTags(e.getTarget()));
		if (rsn.isEmpty())
		{
			return;
		}
		client.getMenu().createMenuEntry(-2)
			.setOption("RuneBeacon invite")
			.setTarget(e.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(me -> SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					clientToolbar.openPanel(navButton);
					panel.inviteByName(rsn);
				}
			}));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!RuneBeaconConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		switch (e.getKey())
		{
			case "enabled":
				if (!config.enabled())
				{
					beaconClient.disconnect(BeaconClient.Status.DISABLED);
					mapPins.clear();
				}
				else
				{
					beaconClient.disconnect(BeaconClient.Status.WAITING_FOR_LOGIN);
				}
				break;
			case "serverUrl":
				// Reconnects on the next tick with the new address.
				beaconClient.disconnect(config.enabled() ? BeaconClient.Status.WAITING_FOR_LOGIN : BeaconClient.Status.DISABLED);
				break;
			case "showMapPins":
			case "mapPinsAllGroups":
			case "showOfflineOnMap":
				dirty = true;
				break;
			default:
				break;
		}
	}

	private void sendPatch()
	{
		if (!beaconClient.isSocketOpen() || !collector.readyToSend())
		{
			return;
		}
		boolean hidden = collector.isHidden();
		if (hidden != appearingOffline)
		{
			appearingOffline = hidden;
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setAppearingOffline(hidden);
				}
			});
		}
		JsonObject patch = collector.buildPatch();
		boolean pvp = collector.isInPvp();
		if (pvp != pingSettings.isPvpPaused())
		{
			pingSettings.setPvpPaused(pvp);
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setPvpPaused(pvp);
				}
			});
		}
		if (patch == null)
		{
			return;
		}
		if (!beaconClient.sendState(patch))
		{
			// Send failed (socket closing); resend everything once reconnected.
			collector.reset();
			return;
		}
		// The server doesn't echo our own patches, so apply them locally for our own card.
		JsonObject copy = patch.deepCopy();
		SwingUtilities.invokeLater(() ->
		{
			String self = store.getSelfUserId();
			if (self != null)
			{
				store.applyPatch(self, copy);
			}
		});
	}

	// ------------------------------------------------------------------ store / connection (EDT)

	@Override
	public void groupsChanged()
	{
		canInvite = !store.groupsWithInvitePermission().isEmpty();
		if (panel != null)
		{
			panel.groupsChanged();
		}
		dirty = true;
	}

	@Override
	public void memberChanged(String userId)
	{
		dirty = true;
	}

	@Override
	public void statusChanged(BeaconClient.Status status, String detail)
	{
		if (status == BeaconClient.Status.CONNECTED)
		{
			// New session: the server has no live state for us yet.
			clientThread.invokeLater(collector::reset);
		}
		if (panel != null)
		{
			panel.statusChanged(status, detail);
		}
	}

	private void onRefreshTimer()
	{
		if (++clockCounter >= CLOCK_REFRESH_TICKS)
		{
			clockCounter = 0;
			dirty = true;
		}
		if (!dirty || panel == null)
		{
			return;
		}
		dirty = false;
		panel.refreshMembers();
		if (config.enabled())
		{
			mapPins.refresh(panel.getSelectedGroupId());
		}
	}

	// ------------------------------------------------------------------ tile pings

	/**
	 * Hotkey + left-click on a tile pings it for the selected group. Mirrors RuneLite's Party
	 * plugin: only when "Walk here" is in the menu (i.e. the click is on the game world), and the
	 * walk click is consumed so the player doesn't also move.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// PvP safe mode: no pings in the Wilderness / PvP areas / PvP worlds; clicks behave normally.
		if (!pingHotkeyHeld || client.isMenuOpen() || !config.enabled() || !beaconClient.isSocketOpen()
			|| pingSettings.isPvpPaused())
		{
			return;
		}
		String groupId = selectedGroupId;
		if (groupId == null)
		{
			return;
		}

		// Hotkey + click on an item in our own inventory pings that item for the group.
		if (WidgetUtil.componentToInterface(event.getParam1()) == InterfaceID.INVENTORY && event.getItemId() > 0)
		{
			if (!config.pingOwnInventory())
			{
				return;
			}
			event.consume();
			int slot = event.getParam0();
			String self = store.getSelfUserId();
			if (self != null && beaconClient.sendItemPing(groupId, self, slot, event.getItemId()))
			{
				java.awt.Color color = UiUtil.memberColor(self);
				int duration = config.pingDuration() * 1000;
				inventoryPingOverlay.add(slot, new InventoryPingOverlay.SlotPing(color, System.currentTimeMillis(), duration));
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.flashItem(self, slot, color, duration);
					}
				});
			}
			return;
		}

		Tile tile = client.getTopLevelWorldView().getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}
		boolean onWorld = false;
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (entry != null && "Walk here".equalsIgnoreCase(entry.getOption()))
			{
				onWorld = true;
				break;
			}
		}
		if (!onWorld)
		{
			return;
		}
		event.consume();

		WorldPoint wp = tile.getWorldLocation();
		if (beaconClient.sendTilePing(groupId, wp.getX(), wp.getY(), wp.getPlane()))
		{
			Player local = client.getLocalPlayer();
			String self = store.getSelfUserId();
			addPing(wp, local == null ? "You" : local.getName(), self == null ? java.awt.Color.WHITE : UiUtil.memberColor(self));
		}
	}

	/** EDT: a group member pinged a tile. */
	private void tilePingReceived(BeaconClient.TilePingEvent e)
	{
		String name = pingAllowed(e.getUserId(), e.getGroupId());
		if (name == null)
		{
			return;
		}
		Integer myWorld = currentWorld;
		if (!config.showPingsOtherWorlds() && e.getWorld() != null && myWorld != null && !e.getWorld().equals(myWorld))
		{
			return;
		}
		addPing(new WorldPoint(e.getX(), e.getY(), e.getPlane()), name, UiUtil.memberColor(e.getUserId()));
		playPingSound();
	}

	/** EDT: a group member pinged an item in someone's inventory (maybe ours). */
	private void itemPingReceived(BeaconClient.ItemPingEvent e)
	{
		String name = pingAllowed(e.getUserId(), e.getGroupId());
		if (name == null)
		{
			return;
		}
		java.awt.Color color = UiUtil.memberColor(e.getUserId());
		int duration = config.pingDuration() * 1000;
		if (panel != null)
		{
			panel.flashItem(e.getTargetUserId(), e.getSlot(), color, duration);
		}
		if (e.getTargetUserId().equals(store.getSelfUserId()))
		{
			if (!config.showInventoryPings())
			{
				return;
			}
			inventoryPingOverlay.add(e.getSlot(), new InventoryPingOverlay.SlotPing(color, System.currentTimeMillis(), duration));
			playPingSound();
		}
	}

	/**
	 * Common ping filters: master toggle, selected-group-only and hidden players.
	 * @return the pinger's name if the ping should be shown, otherwise null
	 */
	private String pingAllowed(String userId, String groupId)
	{
		if (!config.showPings() || pingSettings.isPvpPaused())
		{
			return null;
		}
		if (config.pingsSelectedGroupOnly() && !groupId.equals(selectedGroupId))
		{
			return null;
		}
		Group g = store.getGroup(groupId);
		Member m = g == null ? null : g.findMember(userId);
		if (m == null || pingSettings.isMuted(m.getRsn()))
		{
			return null;
		}
		return m.getRsn();
	}

	private void playPingSound()
	{
		if (config.pingSound())
		{
			clientThread.invokeLater(() -> client.playSoundEffect(SoundEffectID.SMITH_ANVIL_TINK));
		}
	}

	private void addPing(WorldPoint wp, String name, java.awt.Color color)
	{
		tilePingOverlay.add(new TilePingOverlay.Ping(wp, config.showPingNames() ? name : null, color,
			System.currentTimeMillis(), config.pingDuration() * 1000L));
	}

	private void onSelectedGroupChanged(String groupId)
	{
		selectedGroupId = groupId;
		if (groupId != null)
		{
			configManager.setConfiguration(RuneBeaconConfig.GROUP, SELECTED_GROUP_KEY, groupId);
		}
		dirty = true;
	}

	private void retry()
	{
		beaconClient.retry();
	}

	private void inviteReceived(Invite invite)
	{
		if (config.notifyInvites())
		{
			notifier.notify(invite.getInvitedBy() + " invited you to the RuneBeacon group " + invite.getGroupName());
		}
	}

	private void memberCameOnline(String userId)
	{
		if (!config.notifyOnline() || userId.equals(store.getSelfUserId()))
		{
			return;
		}
		for (Group g : store.getGroups())
		{
			Member m = g.findMember(userId);
			if (m != null)
			{
				notifier.notify(m.getRsn() + " is online (" + g.getName() + ")");
				return;
			}
		}
	}
}
