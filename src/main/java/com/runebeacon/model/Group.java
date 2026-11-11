package com.runebeacon.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
public class Group
{
	private String groupId;
	private String name;
	/** "permanent" or "temporary" */
	private String type;
	private Long expiresAt;
	/** Only present for members holding {@link Permission#invite}. */
	private String inviteCode;
	private Role myRole;
	private List<Permission> myPermissions = new ArrayList<>();
	private List<Permission> defaultPermissions = new ArrayList<>();
	private long createdAt;
	private List<Member> members = new ArrayList<>();

	public boolean isTemporary()
	{
		return "temporary".equals(type);
	}

	public boolean isOwner()
	{
		return myRole == Role.owner;
	}

	public Set<Permission> myPermissionSet()
	{
		return Permissions.of(myPermissions);
	}

	public boolean has(Permission p)
	{
		return isOwner() || myPermissionSet().contains(p);
	}

	public Member findMember(String userId)
	{
		for (Member m : members)
		{
			if (m.getUserId().equals(userId))
			{
				return m;
			}
		}
		return null;
	}
}
