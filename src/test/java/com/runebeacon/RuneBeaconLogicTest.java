package com.runebeacon;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runebeacon.model.Group;
import com.runebeacon.model.MemberState;
import com.runebeacon.model.Permission;
import com.runebeacon.model.Permissions;
import com.runebeacon.model.Role;
import java.util.Collections;
import java.util.EnumSet;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RuneBeaconLogicTest
{
	private final Gson gson = new Gson();

	private static JsonObject json(String s)
	{
		return new JsonParser().parse(s).getAsJsonObject();
	}

	@Test
	public void serverUrlIsNormalised()
	{
		assertNull(BeaconClient.parseServerUrl(""));
		assertNull(BeaconClient.parseServerUrl("not a url"));
		assertEquals("https://beacon.example.com/", BeaconClient.parseServerUrl(" https://beacon.example.com/v1/ws?x=1 ").toString());
	}

	@Test
	public void groupParsesFromProtocolJson()
	{
		Group g = gson.fromJson("{\"groupId\":\"g1\",\"name\":\"Raid\",\"type\":\"temporary\",\"expiresAt\":123,"
			+ "\"inviteCode\":\"ABCD2345\",\"myRole\":\"member\",\"myPermissions\":[\"invite\",\"future_perm\"],\"createdAt\":1,"
			+ "\"members\":[{\"userId\":\"u1\",\"rsn\":\"Zezima\",\"role\":\"owner\",\"online\":true,\"lastSeen\":null,"
			+ "\"state\":{\"world\":302}}]}", Group.class);
		assertTrue(g.isTemporary());
		assertTrue(g.has(Permission.invite));
		assertFalse(g.has(Permission.kick));
		assertEquals("unknown permission names are ignored", EnumSet.of(Permission.invite), g.myPermissionSet());
		assertEquals(Role.owner, g.findMember("u1").getRole());

		GroupStore store = new GroupStore(gson);
		store.setGroups(Collections.singletonList(g));
		assertEquals(Integer.valueOf(302), store.getState("u1").getWorld());
		assertNull("snapshot state is moved into the store", g.findMember("u1").getState());
	}

	@Test
	public void patchesMergeAndNullsDelete()
	{
		GroupStore store = new GroupStore(gson);
		store.applyPatch("u1", json("{\"world\":301,\"status\":\"LF team\",\"hp\":[50,99],"
			+ "\"skills\":{\"ATTACK\":[99,99,13034431]},\"inventory\":[995,1000,-1,0]}"));
		store.applyPatch("u1", json("{\"world\":302,\"status\":null}"));

		MemberState s = store.getState("u1");
		assertEquals(Integer.valueOf(302), s.getWorld());
		assertNull(s.getStatus());
		assertArrayEquals(new int[]{50, 99}, s.getHp());
		assertEquals(13034431L, s.getSkills().get("ATTACK")[2]);
		assertArrayEquals(new int[]{995, 1000, -1, 0}, s.getInventory());
	}

	@Test
	public void presenceReportsComingOnline()
	{
		GroupStore store = new GroupStore(gson);
		Group g = gson.fromJson("{\"groupId\":\"g1\",\"name\":\"Friends\",\"type\":\"permanent\",\"myRole\":\"member\","
			+ "\"members\":[{\"userId\":\"u1\",\"rsn\":\"A\",\"role\":\"member\",\"online\":false}]}", Group.class);
		store.setGroups(Collections.singletonList(g));
		assertTrue(store.setPresence("u1", true, null));
		assertFalse("already online", store.setPresence("u1", true, null));
		assertFalse(store.setPresence("u1", false, 5L));
		assertEquals(Long.valueOf(5L), g.findMember("u1").getLastSeen());
	}

	private Group groupAs(String myRole, String myPerms, String members)
	{
		return gson.fromJson("{\"groupId\":\"g\",\"name\":\"G\",\"type\":\"permanent\",\"myRole\":\"" + myRole
			+ "\",\"myPermissions\":" + myPerms + ",\"members\":" + members + "}", Group.class);
	}

	private static String member(String id, String role, String perms)
	{
		return "{\"userId\":\"" + id + "\",\"rsn\":\"" + id + "\",\"role\":\"" + role + "\",\"permissions\":" + perms + "}";
	}

	@Test
	public void permissionHierarchy()
	{
		String members = "[" + member("me", "member", "[\"kick\",\"manage_permissions\",\"invite\"]") + ","
			+ member("owner", "owner", "[\"invite\",\"kick\",\"rename\",\"extend\",\"manage_permissions\"]") + ","
			+ member("lower", "member", "[\"kick\"]") + ","
			+ member("peer", "member", "[\"kick\",\"manage_permissions\",\"invite\"]") + ","
			+ member("other", "member", "[\"rename\"]") + ","
			+ member("plain", "member", "[]") + "]";
		Group g = groupAs("member", "[\"kick\",\"manage_permissions\",\"invite\"]", members);

		assertTrue(Permissions.canKick(g, g.findMember("lower"), "me"));
		assertTrue(Permissions.canKick(g, g.findMember("plain"), "me"));
		assertFalse("equal sets can't act on each other", Permissions.canKick(g, g.findMember("peer"), "me"));
		assertFalse("not a subset", Permissions.canKick(g, g.findMember("other"), "me"));
		assertFalse(Permissions.canKick(g, g.findMember("owner"), "me"));
		assertFalse("never yourself", Permissions.canKick(g, g.findMember("me"), "me"));
		assertTrue(Permissions.canEditPermissions(g, g.findMember("lower"), "me"));

		// Grant rule: strict subset of own set.
		assertTrue(Permissions.canGrant(g, EnumSet.of(Permission.kick, Permission.invite)));
		assertFalse(Permissions.canGrant(g, EnumSet.of(Permission.kick, Permission.invite, Permission.manage_permissions)));
		assertFalse(Permissions.canGrant(g, EnumSet.of(Permission.rename)));

		// Without the kick permission nothing is kickable.
		Group noKick = groupAs("member", "[\"manage_permissions\",\"invite\"]", members);
		assertFalse(Permissions.canKick(noKick, noKick.findMember("plain"), "me"));
		assertTrue(Permissions.canEditPermissions(noKick, noKick.findMember("plain"), "me"));

		// The owner can act on anyone but themselves and grant anything.
		Group asOwner = groupAs("owner", "[]", members);
		assertTrue(Permissions.canKick(asOwner, asOwner.findMember("peer"), "owner"));
		assertFalse(Permissions.canKick(asOwner, asOwner.findMember("owner"), "owner"));
		assertTrue(Permissions.canGrant(asOwner, EnumSet.allOf(Permission.class)));
	}

	@Test
	public void regionDataLoads()
	{
		RegionData data = new RegionData(gson);
		assertEquals("Varrock", data.areaName(12853));
		String[] zulrah = data.activity(9007);
		assertNotNull(zulrah);
		assertEquals("boss", zulrah[0]);
		assertEquals("Zulrah", zulrah[1]);
		assertEquals("raid", data.activity(12613)[0]);
	}
}
