package com.runebeacon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runebeacon.model.Group;
import com.runebeacon.model.Invite;
import com.runebeacon.model.Member;
import com.runebeacon.model.MemberState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

/**
 * Groups, memberships and member state. Confined to the Swing EDT: every mutation and read must
 * happen there (the socket listener hops onto the EDT before touching it).
 */
@Singleton
public class GroupStore
{
	public interface Listener
	{
		/** Group list or membership changed — rebuild structure. */
		void groupsChanged();

		/** A member's state or presence changed — refresh values. */
		void memberChanged(String userId);
	}

	private final Gson gson;
	private final Map<String, Group> groups = new LinkedHashMap<>();
	/** userId -> merged raw state */
	private final Map<String, JsonObject> states = new HashMap<>();
	/** userId -> parsed state cache, invalidated on patch */
	private final Map<String, MemberState> parsed = new HashMap<>();
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final Map<String, Invite> invites = new LinkedHashMap<>();

	@Getter
	@Setter
	private String selfUserId;

	@Inject
	GroupStore(Gson gson)
	{
		this.gson = gson;
	}

	public void addListener(Listener l)
	{
		listeners.add(l);
	}

	public void removeListener(Listener l)
	{
		listeners.remove(l);
	}

	public Collection<Group> getGroups()
	{
		return groups.values();
	}

	public Group getGroup(String groupId)
	{
		return groupId == null ? null : groups.get(groupId);
	}

	public void clear()
	{
		groups.clear();
		states.clear();
		parsed.clear();
		invites.clear();
		selfUserId = null;
		fireGroupsChanged();
	}

	// ------------------------------------------------------------------ direct invites

	public List<Invite> getInvites()
	{
		return new ArrayList<>(invites.values());
	}

	public void setInvites(List<Invite> list)
	{
		invites.clear();
		if (list != null)
		{
			for (Invite i : list)
			{
				invites.put(i.getInviteId(), i);
			}
		}
		fireGroupsChanged();
	}

	public void upsertInvite(Invite invite)
	{
		invites.put(invite.getInviteId(), invite);
		fireGroupsChanged();
	}

	public void removeInvite(String inviteId)
	{
		if (invites.remove(inviteId) != null)
		{
			fireGroupsChanged();
		}
	}

	/** Groups in which the viewer may invite people. */
	public List<Group> groupsWithInvitePermission()
	{
		List<Group> out = new ArrayList<>();
		for (Group g : groups.values())
		{
			if (g.has(com.runebeacon.model.Permission.invite))
			{
				out.add(g);
			}
		}
		return out;
	}

	public void setGroups(List<Group> list)
	{
		groups.clear();
		for (Group g : list)
		{
			absorbMemberStates(g);
			groups.put(g.getGroupId(), g);
		}
		fireGroupsChanged();
	}

	public void upsertGroup(Group g)
	{
		absorbMemberStates(g);
		groups.put(g.getGroupId(), g);
		fireGroupsChanged();
	}

	public void removeGroup(String groupId)
	{
		if (groups.remove(groupId) != null)
		{
			fireGroupsChanged();
		}
	}

	public void applyPatch(String userId, JsonObject patch)
	{
		JsonObject state = states.computeIfAbsent(userId, k -> new JsonObject());
		for (Map.Entry<String, JsonElement> e : patch.entrySet())
		{
			if (e.getValue() == null || e.getValue().isJsonNull())
			{
				state.remove(e.getKey());
			}
			else
			{
				state.add(e.getKey(), e.getValue());
			}
		}
		parsed.remove(userId);
		if (patch.has("rsn") && patch.get("rsn").isJsonPrimitive())
		{
			String rsn = patch.get("rsn").getAsString();
			forEachMembership(userId, m -> m.setRsn(rsn));
		}
		fireMemberChanged(userId);
	}

	/** @return true if the member just came online (was offline before) */
	public boolean setPresence(String userId, boolean online, Long lastSeen)
	{
		boolean[] cameOnline = {false};
		forEachMembership(userId, m ->
		{
			if (online && !m.isOnline())
			{
				cameOnline[0] = true;
			}
			m.setOnline(online);
			if (lastSeen != null)
			{
				m.setLastSeen(lastSeen);
			}
		});
		fireMemberChanged(userId);
		return cameOnline[0];
	}

	public MemberState getState(String userId)
	{
		MemberState s = parsed.get(userId);
		if (s == null)
		{
			JsonObject raw = states.get(userId);
			if (raw == null)
			{
				return null;
			}
			s = gson.fromJson(raw, MemberState.class);
			parsed.put(userId, s);
		}
		return s;
	}

	/** Distinct members across the given groups, keyed by user id (first membership wins). */
	public Map<String, Member> distinctMembers(Collection<Group> source)
	{
		Map<String, Member> out = new LinkedHashMap<>();
		for (Group g : source)
		{
			for (Member m : g.getMembers())
			{
				out.putIfAbsent(m.getUserId(), m);
			}
		}
		return out;
	}

	public List<Group> groupsList()
	{
		return new ArrayList<>(groups.values());
	}

	private void absorbMemberStates(Group g)
	{
		for (Member m : g.getMembers())
		{
			if (m.getState() != null)
			{
				// A REST/hello snapshot is authoritative for that moment.
				states.put(m.getUserId(), m.getState());
				parsed.remove(m.getUserId());
				m.setState(null);
			}
		}
	}

	private void forEachMembership(String userId, java.util.function.Consumer<Member> fn)
	{
		for (Group g : groups.values())
		{
			Member m = g.findMember(userId);
			if (m != null)
			{
				fn.accept(m);
			}
		}
	}

	private void fireGroupsChanged()
	{
		for (Listener l : listeners)
		{
			l.groupsChanged();
		}
	}

	private void fireMemberChanged(String userId)
	{
		for (Listener l : listeners)
		{
			l.memberChanged(userId);
		}
	}
}
