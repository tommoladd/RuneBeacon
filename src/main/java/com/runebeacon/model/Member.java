package com.runebeacon.model;

import lombok.Data;

/**
 * Group membership row as sent by the server. Live state is not kept here; it lives in
 * {@link com.runebeacon.GroupStore} keyed by user id, since a user can be in several groups.
 */
@Data
public class Member
{
	private String userId;
	private String rsn;
	private Role role;
	private java.util.List<Permission> permissions = new java.util.ArrayList<>();
	private boolean online;
	private Long lastSeen;
	/** Last known state from REST/hello snapshots. Consumed by the store on load. */
	private com.google.gson.JsonObject state;
}
