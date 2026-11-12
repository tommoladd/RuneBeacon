package com.runebeacon.ui;

import com.runebeacon.model.Member;
import com.runebeacon.model.MemberState;
import java.awt.Color;

public final class UiUtil
{
	public static final Color ONLINE = new Color(0, 200, 83);
	public static final Color IDLE = new Color(255, 180, 0);
	public static final Color OFFLINE = new Color(120, 120, 120);
	public static final Color HP = new Color(196, 33, 33);

	// In-game interface palette.
	public static final Color RS_BROWN = new Color(62, 53, 41);
	public static final Color RS_BAR = new Color(43, 37, 30);
	public static final Color RS_SLOT = new Color(75, 66, 54);
	public static final Color RS_YELLOW = new Color(255, 255, 0);
	public static final Color RS_ORANGE = new Color(255, 152, 31);
	public static final Color RS_POISON = new Color(0, 200, 0);
	public static final Color RS_VENOM = new Color(0, 110, 60);

	private UiUtil()
	{
	}

	public static String titleCase(String enumName)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : enumName.split("_"))
		{
			if (part.isEmpty())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
		}
		return sb.toString();
	}

	public static String itemLabel(String name, int qty, boolean stackable)
	{
		return name + (stackable || qty > 1 ? " x" + String.format("%,d", qty) : "");
	}

	/** RuneScape-style text: 1px black drop shadow. */
	public static void drawShadowed(java.awt.Graphics g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	public static void drawShadowedCentered(java.awt.Graphics g, String text, int cx, int cy, Color color)
	{
		g.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		java.awt.FontMetrics fm = g.getFontMetrics();
		drawShadowed(g, text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2, color);
	}

	/** Orb-style colour for a current/max value: green when high, through yellow, to red. */
	public static Color levelColor(int current, int max)
	{
		if (max <= 0)
		{
			return RS_YELLOW;
		}
		float ratio = Math.max(0f, Math.min(1f, (float) current / max));
		return ratio >= 0.5f
			? new Color((int) (255 * (1 - ratio) * 2), 255, 0)
			: new Color(255, (int) (255 * ratio * 2), 0);
	}

	/** A stable, distinct-ish colour per member. */
	public static Color memberColor(String userId)
	{
		float hue = (userId.hashCode() & 0x7fffffff) % 360 / 360f;
		return Color.getHSBColor(hue, 0.65f, 0.95f);
	}

	/** Member is appearing offline (their private chat is Off). */
	public static boolean isHidden(MemberState s)
	{
		return s != null && Boolean.TRUE.equals(s.getHidden());
	}

	public static boolean isLoggedIn(Member m, MemberState s)
	{
		return m.isOnline() && s != null && !isHidden(s) && !"LOGIN_SCREEN".equals(s.getGameState());
	}

	public static Color presenceColor(Member m, MemberState s)
	{
		if (!isLoggedIn(m, s))
		{
			return OFFLINE;
		}
		return Boolean.TRUE.equals(s.getIdle()) ? IDLE : ONLINE;
	}

	public static String presenceText(Member m, MemberState s)
	{
		if (!m.isOnline() || isHidden(s))
		{
			return m.getLastSeen() == null ? "Offline" : "Seen " + timeAgo(m.getLastSeen());
		}
		if (s == null || "LOGIN_SCREEN".equals(s.getGameState()))
		{
			return "Logged out";
		}
		if ("HOPPING".equals(s.getGameState()))
		{
			return "Hopping";
		}
		String world = s.getWorld() == null ? "" : "W" + s.getWorld();
		return Boolean.TRUE.equals(s.getIdle()) ? world + " (AFK)" : world;
	}

	/** What they're doing, best first: activity, training, area. */
	public static String doing(MemberState s)
	{
		if (s == null)
		{
			return null;
		}
		String where = s.getArea();
		// PvP safe mode sends only a generic label and no location.
		if (s.getLoc() == null && "Wilderness".equals(where))
		{
			return "In the Wilderness";
		}
		if (s.getLoc() == null && "PvP world".equals(where))
		{
			return "On a PvP world";
		}
		if (s.getActivity() != null && s.getActivity().getName() != null)
		{
			String verb;
			switch (String.valueOf(s.getActivity().getType()))
			{
				case "boss":
					verb = "Fighting ";
					break;
				case "raid":
					verb = "Raiding ";
					break;
				default:
					verb = "At ";
			}
			return verb + s.getActivity().getName();
		}
		if (s.getTraining() != null)
		{
			return "Training " + s.getTraining() + (where != null ? " in " + where : "");
		}
		return where;
	}

	public static String describe(Member m, MemberState s)
	{
		StringBuilder sb = new StringBuilder(m.getRsn());
		String presence = presenceText(m, s);
		if (!presence.isEmpty())
		{
			sb.append(" - ").append(presence);
		}
		String doing = doing(s);
		if (doing != null)
		{
			sb.append(" - ").append(doing);
		}
		if (s != null && s.getStatus() != null)
		{
			sb.append(" - \"").append(s.getStatus()).append('"');
		}
		return sb.toString();
	}

	public static String timeAgo(long epochMs)
	{
		long secs = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000);
		if (secs < 60)
		{
			return "just now";
		}
		long mins = secs / 60;
		if (mins < 60)
		{
			return mins + "m ago";
		}
		long hours = mins / 60;
		if (hours < 48)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}

	public static String timeLeft(long epochMs)
	{
		long mins = Math.max(0, (epochMs - System.currentTimeMillis()) / 60000);
		if (mins < 60)
		{
			return mins + "m left";
		}
		return (mins / 60) + "h " + (mins % 60) + "m left";
	}
}
