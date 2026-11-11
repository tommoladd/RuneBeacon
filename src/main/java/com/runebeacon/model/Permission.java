package com.runebeacon.model;

import lombok.Getter;

// Lower-case constants so Gson maps the protocol strings directly.
public enum Permission
{
	invite("Invite", "See and regenerate the invite code"),
	kick("Kick", "Remove members with fewer permissions"),
	rename("Rename", "Rename the group"),
	extend("Extend", "Extend a temporary group's expiry"),
	manage_permissions("Manage permissions", "Change permissions of members with fewer permissions");

	@Getter
	private final String displayName;
	@Getter
	private final String description;

	Permission(String displayName, String description)
	{
		this.displayName = displayName;
		this.description = description;
	}
}
