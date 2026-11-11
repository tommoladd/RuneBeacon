package com.runebeacon.ui;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;

/** Shared UI helpers passed down to cards, so constructors stay small. */
@Singleton
@Getter
public class PanelServices
{
	private final ItemManager itemManager;
	private final ItemInfoCache itemInfo;
	private final SpriteCache sprites;
	private final SkillIconManager skillIcons;
	private final PingSettings pingSettings;

	@Inject
	PanelServices(ItemManager itemManager, ItemInfoCache itemInfo, SpriteCache sprites, SkillIconManager skillIcons,
		PingSettings pingSettings)
	{
		this.itemManager = itemManager;
		this.itemInfo = itemInfo;
		this.sprites = sprites;
		this.skillIcons = skillIcons;
		this.pingSettings = pingSettings;
	}
}
