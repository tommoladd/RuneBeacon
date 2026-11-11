package com.runebeacon;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RuneBeaconConfig.GROUP)
public interface RuneBeaconConfig extends Config
{
	String GROUP = "runebeacon";

	@ConfigItem(
		keyName = "enabled",
		name = "Connect to RuneBeacon server",
		description = "Share your status with your RuneBeacon groups and see theirs",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean enabled()
	{
		return false;
	}

	/** The public RuneBeacon server every normal install connects to. */
	String DEFAULT_SERVER_URL = "https://beacon.tommoladd.dev";

	/**
	 * Developer-mode override for the server address (e.g. http://localhost:3000). Hidden from the
	 * settings panel and ignored unless RuneLite runs with --developer-mode; edit it from the
	 * "Dev server" row in the RuneBeacon panel.
	 */
	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL (developer mode)",
		description = "Developer mode only: override the RuneBeacon server address",
		hidden = true,
		position = 1
	)
	default String serverUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "customStatus",
		name = "Status message",
		description = "A short message shown to your groups, e.g. 'LF Barrows duo' (max 64 characters)",
		position = 2
	)
	default String customStatus()
	{
		return "";
	}

	@ConfigSection(
		name = "Sharing",
		description = "Choose what you share with your groups",
		position = 10
	)
	String sharingSection = "sharing";

	@ConfigItem(keyName = "shareLocation", name = "Location & area", description = "Share your map position and area name", section = sharingSection, position = 11)
	default boolean shareLocation()
	{
		return true;
	}

	@ConfigItem(keyName = "shareActivity", name = "Activity", description = "Share the boss/raid/minigame you're at and the skill you're training", section = sharingSection, position = 12)
	default boolean shareActivity()
	{
		return true;
	}

	@ConfigItem(keyName = "shareVitals", name = "HP, prayer, spec & run", description = "Share hitpoints, prayer points, special attack, run energy and poison status", section = sharingSection, position = 13)
	default boolean shareVitals()
	{
		return true;
	}

	@ConfigItem(keyName = "shareSkills", name = "Skills", description = "Share your skill levels and experience", section = sharingSection, position = 14)
	default boolean shareSkills()
	{
		return true;
	}

	@ConfigItem(keyName = "shareInventory", name = "Inventory & rune pouch", description = "Share your inventory and rune pouch", section = sharingSection, position = 15)
	default boolean shareInventory()
	{
		return true;
	}

	@ConfigItem(keyName = "shareEquipment", name = "Equipment", description = "Share your worn equipment, including Dizana's quiver ammo", section = sharingSection, position = 16)
	default boolean shareEquipment()
	{
		return true;
	}

	@ConfigItem(keyName = "sharePrayers", name = "Active prayers", description = "Share which prayers you have active", section = sharingSection, position = 17)
	default boolean sharePrayers()
	{
		return true;
	}

	@ConfigSection(
		name = "Tile pings",
		description = "Hold the hotkey and click a tile to highlight it for your selected group",
		position = 18
	)
	String pingSection = "pings";

	@ConfigItem(keyName = "pingHotkey", name = "Ping hotkey", description = "Hold this and click a tile or an inventory item (in game or in the RuneBeacon panel) to ping it for your selected group", section = pingSection, position = 0)
	default Keybind pingHotkey()
	{
		return Keybind.SHIFT;
	}

	@ConfigItem(keyName = "showPings", name = "Show pings", description = "Show tile and item pings from group members", section = pingSection, position = 1)
	default boolean showPings()
	{
		return true;
	}

	@ConfigItem(keyName = "pingSound", name = "Ping sound", description = "Play a sound when a group member pings a tile or item", section = pingSection, position = 2)
	default boolean pingSound()
	{
		return true;
	}

	@ConfigItem(keyName = "showInventoryPings", name = "Show inventory pings", description = "Highlight items in your inventory when a group member pings them", section = pingSection, position = 3)
	default boolean showInventoryPings()
	{
		return true;
	}

	@ConfigItem(keyName = "showPingsOtherWorlds", name = "Show pings from other worlds", description = "Show tile pings from members on a different world to you", section = pingSection, position = 4)
	default boolean showPingsOtherWorlds()
	{
		return false;
	}

	@ConfigItem(keyName = "pingsSelectedGroupOnly", name = "Selected group only", description = "Only show pings from the group selected in the RuneBeacon panel", section = pingSection, position = 5)
	default boolean pingsSelectedGroupOnly()
	{
		return false;
	}

	@ConfigItem(keyName = "showPingNames", name = "Show names on pings", description = "Show who pinged above the highlighted tile", section = pingSection, position = 6)
	default boolean showPingNames()
	{
		return true;
	}

	@Range(min = 1, max = 10)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "pingDuration", name = "Ping duration", description = "How long pings stay on screen", section = pingSection, position = 7)
	default int pingDuration()
	{
		return 3;
	}

	@ConfigItem(keyName = PING_HIDDEN_KEY, name = "Hidden players", description = "Pings from these players are ignored (comma separated). Right-click a member in the panel to add or remove them.", section = pingSection, position = 8)
	default String pingHiddenPlayers()
	{
		return "";
	}

	String PING_HIDDEN_KEY = "pingHiddenPlayers";

	@ConfigItem(keyName = "pingOwnInventory", name = "Ping own inventory items", description = "Hold the ping hotkey and click an item in your inventory to ping it for your group. "
		+ "Off by default because, while the hotkey is held, it replaces the item's normal click (including shift-drop if the hotkey is Shift).", section = pingSection, position = 9)
	default boolean pingOwnInventory()
	{
		return false;
	}

	@ConfigSection(
		name = "Display",
		description = "How group members are shown",
		position = 20
	)
	String displaySection = "display";

	@ConfigItem(keyName = "showMapPins", name = "World map pins", description = "Show group members on the world map", section = displaySection, position = 21)
	default boolean showMapPins()
	{
		return true;
	}

	@ConfigItem(keyName = "mapPinsAllGroups", name = "Pins for all groups", description = "Show members of all your groups on the map, not just the selected group", section = displaySection, position = 22)
	default boolean mapPinsAllGroups()
	{
		return true;
	}

	@ConfigItem(keyName = "showOfflineOnMap", name = "Show offline members", description = "Show offline members on the map at their last known location", section = displaySection, position = 23)
	default boolean showOfflineOnMap()
	{
		return false;
	}

	@ConfigItem(keyName = "notifyOnline", name = "Notify when members log in", description = "Send a notification when a group member comes online", section = displaySection, position = 24)
	default boolean notifyOnline()
	{
		return false;
	}

	@ConfigItem(keyName = "notifyInvites", name = "Notify on group invites", description = "Send a notification when someone invites you to a group", section = displaySection, position = 25)
	default boolean notifyInvites()
	{
		return true;
	}

	@ConfigItem(keyName = "inviteMenuOption", name = "Right-click invite option", description = "Add 'RuneBeacon invite' when right-clicking names in your friends list, clan and chat", section = displaySection, position = 26)
	default boolean inviteMenuOption()
	{
		return true;
	}
}
