package com.runebeacon.ui;

import com.runebeacon.RuneBeaconConfig;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * The "Hidden players" ping list, stored as comma-separated RSNs in the plugin config so users
 * can also edit it in the settings panel. Matching is case- and separator-insensitive.
 */
@Singleton
public class PingSettings
{
	private final RuneBeaconConfig config;
	private final ConfigManager configManager;

	@Inject
	PingSettings(RuneBeaconConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	/** Set by the plugin's key listener while the ping hotkey is held (game canvas focused). */
	@lombok.Getter
	@lombok.Setter
	private volatile boolean hotkeyHeld;

	/** PvP safe mode: we're in the Wilderness / a PvP area / on a PvP world, so pings are off. */
	@lombok.Getter
	@lombok.Setter
	private volatile boolean pvpPaused;

	/**
	 * Is this panel click a "ping click"? True while the hotkey is held, or — because the side
	 * panel doesn't receive game key events — when a modifier-only hotkey (Shift/Ctrl/Alt) is
	 * part of the click itself.
	 */
	public boolean isPingClick(java.awt.event.MouseEvent e)
	{
		net.runelite.client.config.Keybind kb = config.pingHotkey();
		if (kb == null || net.runelite.client.config.Keybind.NOT_SET.equals(kb))
		{
			return false;
		}
		if (hotkeyHeld)
		{
			return true;
		}
		int mods = kb.getModifiers();
		return kb.getKeyCode() == java.awt.event.KeyEvent.VK_UNDEFINED && mods != 0 && (e.getModifiersEx() & mods) == mods;
	}

	public int durationMs()
	{
		return Math.max(1, config.pingDuration()) * 1000;
	}

	public boolean isMuted(String rsn)
	{
		if (rsn == null)
		{
			return false;
		}
		String key = normalise(rsn);
		for (String name : names())
		{
			if (normalise(name).equals(key))
			{
				return true;
			}
		}
		return false;
	}

	public void setMuted(String rsn, boolean muted)
	{
		List<String> names = names();
		String key = normalise(rsn);
		names.removeIf(n -> normalise(n).equals(key));
		if (muted)
		{
			names.add(rsn);
		}
		configManager.setConfiguration(RuneBeaconConfig.GROUP, RuneBeaconConfig.PING_HIDDEN_KEY, String.join(", ", names));
	}

	private List<String> names()
	{
		List<String> out = new ArrayList<>();
		String raw = config.pingHiddenPlayers();
		if (raw != null)
		{
			for (String part : raw.split(","))
			{
				String n = part.trim();
				if (!n.isEmpty())
				{
					out.add(n);
				}
			}
		}
		return out;
	}

	private static String normalise(String rsn)
	{
		return Text.toJagexName(rsn).toLowerCase().replace(' ', ' ').replace('_', ' ').replace('-', ' ');
	}
}
