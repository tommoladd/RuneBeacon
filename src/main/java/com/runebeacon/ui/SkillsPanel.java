package com.runebeacon.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.JComponent;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;

/** Skill levels drawn with the in-game stat tiles, in the same order as the Skills tab. */
class SkillsPanel extends JComponent
{
	private static final Skill[] ORDER = {
		Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
		Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
		Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
		Skill.RANGED, Skill.THIEVING, Skill.COOKING,
		Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
		Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
		Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
		Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
	};
	private static final int COLS = 3;
	private static final int TILE_H = 32;
	private static final int GAP = 2;
	private static final int TOTAL_H = 22;

	private final SpriteCache sprites;
	private final SkillIconManager skillIcons;
	private Map<String, long[]> skills;
	private Integer total;

	SkillsPanel(SpriteCache sprites, SkillIconManager skillIcons)
	{
		this.sprites = sprites;
		this.skillIcons = skillIcons;
		setOpaque(true);
		setBackground(UiUtil.RS_BROWN);
		int rows = (ORDER.length + COLS - 1) / COLS;
		setPreferredSize(new Dimension(0, 4 + rows * (TILE_H + GAP) + TOTAL_H + 6));
		setToolTipText("");
	}

	void setSkills(Map<String, long[]> skills, Integer total)
	{
		this.skills = skills;
		this.total = total;
		repaint();
	}

	private int tileW()
	{
		return (getWidth() - 8 - GAP * (COLS - 1)) / COLS;
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0;
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());
		if (skills == null)
		{
			UiUtil.drawShadowedCentered(g, "Not shared", getWidth() / 2, getHeight() / 2, UiUtil.RS_ORANGE);
			return;
		}

		BufferedImage left = sprites.peek(SpriteID.Miscgraphics.STATS_TILE_HALF_LEFT, this::repaint);
		BufferedImage right = sprites.peek(SpriteID.Miscgraphics.STATS_TILE_HALF_RIGHT_WITH_SLASH, this::repaint);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int tw = tileW();
		int half = tw / 2;

		for (int i = 0; i < ORDER.length; i++)
		{
			int x = 4 + (i % COLS) * (tw + GAP);
			int y = 4 + (i / COLS) * (TILE_H + GAP);
			if (left != null && right != null)
			{
				g.drawImage(left, x, y, half, TILE_H, null);
				g.drawImage(right, x + half, y, tw - half, TILE_H, null);
			}
			else
			{
				g.setColor(UiUtil.RS_SLOT);
				g.fillRect(x, y, tw, TILE_H);
			}

			Skill skill = ORDER[i];
			BufferedImage icon = skillIcons.getSkillImage(skill, false);
			if (icon != null)
			{
				g.drawImage(icon, x + (half - icon.getWidth()) / 2, y + (TILE_H - icon.getHeight()) / 2, null);
			}

			long[] v = skills.get(skill.name());
			if (v != null && v.length >= 2)
			{
				String boosted = String.valueOf(v[0]);
				String real = String.valueOf(v[1]);
				int rx = x + half;
				int rw = tw - half;
				UiUtil.drawShadowed(g, boosted, rx + rw / 2 - fm.stringWidth(boosted) - 1, y + fm.getAscent() + 1, UiUtil.RS_YELLOW);
				UiUtil.drawShadowed(g, real, rx + rw - fm.stringWidth(real) - 3, y + TILE_H - 3, UiUtil.RS_YELLOW);
			}
		}

		int rows = (ORDER.length + COLS - 1) / COLS;
		int ty = 4 + rows * (TILE_H + GAP) + 2;
		g.setColor(Color.BLACK);
		g.fillRect(4, ty, getWidth() - 8, TOTAL_H);
		g.setColor(UiUtil.RS_BAR);
		g.drawRect(4, ty, getWidth() - 9, TOTAL_H - 1);
		String totalText = "Total level: " + (total == null ? "?" : total);
		UiUtil.drawShadowed(g, totalText, (getWidth() - fm.stringWidth(totalText)) / 2, ty + (TOTAL_H + fm.getAscent()) / 2 - 1, UiUtil.RS_YELLOW);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		if (skills == null)
		{
			return null;
		}
		int tw = tileW();
		int col = (e.getX() - 4) / (tw + GAP);
		int row = (e.getY() - 4) / (TILE_H + GAP);
		int i = row * COLS + col;
		if (col < 0 || col >= COLS || row < 0 || i >= ORDER.length)
		{
			return null;
		}
		long[] v = skills.get(ORDER[i].name());
		if (v == null || v.length < 3)
		{
			return null;
		}
		return ORDER[i].getName() + ": " + String.format("%,d", v[2]) + " xp";
	}
}
