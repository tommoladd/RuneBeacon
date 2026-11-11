package com.runebeacon.model;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Client-side mirror of the server's permission rules (PROTOCOL.md "Permissions"), used only to
 * decide which actions to offer. The server enforces the rules regardless.
 */
public final class Permissions
{
	private Permissions()
	{
	}

	public static Set<Permission> of(Collection<Permission> list)
	{
		Set<Permission> set = EnumSet.noneOf(Permission.class);
		if (list != null)
		{
			for (Permission p : list)
			{
				// Gson maps unknown names (from a newer server) to null.
				if (p != null)
				{
					set.add(p);
				}
			}
		}
		return set;
	}

	/** Hierarchy rule: may the viewer kick / edit permissions of this target at all? */
	public static boolean outranks(Group group, Member target, String selfUserId)
	{
		if (target.getRole() == Role.owner || target.getUserId().equals(selfUserId))
		{
			return false;
		}
		if (group.getMyRole() == Role.owner)
		{
			return true;
		}
		return isStrictSubset(of(target.getPermissions()), group.myPermissionSet());
	}

	public static boolean canKick(Group group, Member target, String selfUserId)
	{
		return group.has(Permission.kick) && outranks(group, target, selfUserId);
	}

	public static boolean canEditPermissions(Group group, Member target, String selfUserId)
	{
		return group.has(Permission.manage_permissions) && outranks(group, target, selfUserId);
	}

	/** Grant rule: may the viewer set a member's permissions to exactly this set? */
	public static boolean canGrant(Group group, Set<Permission> newSet)
	{
		return group.getMyRole() == Role.owner || isStrictSubset(newSet, group.myPermissionSet());
	}

	/** Permissions the viewer could ever hand out (owner: all, others: their own). */
	public static Set<Permission> grantable(Group group)
	{
		return group.getMyRole() == Role.owner ? EnumSet.allOf(Permission.class) : group.myPermissionSet();
	}

	static boolean isStrictSubset(Set<Permission> a, Set<Permission> b)
	{
		return b.containsAll(a) && b.size() > a.size();
	}
}
