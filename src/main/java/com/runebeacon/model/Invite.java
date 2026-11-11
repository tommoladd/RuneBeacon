package com.runebeacon.model;

import lombok.Data;

/** A pending direct invite to a group (PROTOCOL.md "Direct invites"). */
@Data
public class Invite
{
	private String inviteId;
	private String groupId;
	private String groupName;
	/** "permanent" or "temporary" */
	private String groupType;
	private String invitedBy;
	private int memberCount;
	private long createdAt;
	private long expiresAt;
}
