package com.runebeacon.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;

/**
 * Worn equipment drawn like the in-game equipment tab: slot tiles, empty-slot silhouettes and
 * the connecting bars. Dizana's quiver ammo gets an extra tile above the ammo slot.
 */
class GearPanel extends JComponent
{
	private static final int TILE = 36;
	private static final int COL_PITCH = 46;
	private static final int ROW_PITCH = 40;
	private static final int ROWS = 5;
	private static final int BAR = 4;

	/** [slot, col, row, placeholder sprite] — matches the in-game layout. */
	private static final Object[][] SLOTS = {
		{EquipmentInventorySlot.HEAD, 1, 0, SpriteID.Wornicons.HEAD},
		{EquipmentInventorySlot.CAPE, 0, 1, SpriteID.Wornicons.CAPE},
		{EquipmentInventorySlot.AMULET, 1, 1, SpriteID.Wornicons.NECK},
		{EquipmentInventorySlot.AMMO, 2, 1, SpriteID.Wornicons.AMMUNITION},
		{EquipmentInventorySlot.WEAPON, 0, 2, SpriteID.Wornicons.WEAPON},
		{EquipmentInventorySlot.BODY, 1, 2, SpriteID.Wornicons.TORSO},
		{EquipmentInventorySlot.SHIELD, 2, 2, SpriteID.Wornicons.SHIELD},
		{EquipmentInventorySlot.LEGS, 1, 3, SpriteID.Wornicons.LEGS},
		{EquipmentInventorySlot.GLOVES, 0, 4, SpriteID.Wornicons.HANDS},
		{EquipmentInventorySlot.BOOTS, 1, 4, SpriteID.Wornicons.FEET},
		{EquipmentInventorySlot.RING, 2, 4, SpriteID.Wornicons.RING},
	};
	private static final int QUIVER_COL = 2;
	private static final int QUIVER_ROW = 0;

	private final SpriteCache sprites;
	private final ItemManager itemManager;
	private final ItemInfoCache itemInfo;

	private int[] equipment;
	private int[] quiver;
	private final BufferedImage[] slotImages = new BufferedImage[14];
	private BufferedImage quiverImage;
	private final String[] tooltips = new String[15];

	GearPanel(SpriteCache sprites, ItemManager itemManager, ItemInfoCache itemInfo)
	{
		this.sprites = sprites;
		this.itemManager = itemManager;
		this.itemInfo = itemInfo;
		setOpaque(true);
		setBackground(UiUtil.RS_BROWN);
		setPreferredSize(new Dimension(0, ROWS * ROW_PITCH + 8));
		setToolTipText("");
	}

	void setGear(int[] equipment, int[] quiver)
	{
		this.equipment = equipment;
		this.quiver = quiver;
		java.util.Arrays.fill(slotImages, null);
		java.util.Arrays.fill(tooltips, null);
		quiverImage = null;
		for (Object[] s : SLOTS)
		{
			EquipmentInventorySlot slot = (EquipmentInventorySlot) s[0];
			int i = slot.getSlotIdx();
			tooltips[i] = UiUtil.titleCase(slot.name()) + ": empty";
			if (equipment != null && i * 2 + 1 < equipment.length && equipment[i * 2] > -1)
			{
				loadItem(equipment[i * 2], equipment[i * 2 + 1], i, null);
			}
		}
		if (quiver != null && quiver.length >= 2)
		{
			loadItem(quiver[0], quiver[1], 14, "Quiver");
		}
		repaint();
	}

	private void loadItem(int itemId, int qty, int index, String prefix)
	{
		itemInfo.lookup(itemId, info ->
		{
			BufferedImage img = itemManager.getImage(itemId, qty, info.stackable);
			if (img instanceof net.runelite.client.util.AsyncBufferedImage)
			{
				((net.runelite.client.util.AsyncBufferedImage) img).onLoaded(this::repaint);
			}
			if (index == 14)
			{
				quiverImage = img;
			}
			else
			{
				slotImages[index] = img;
			}
			tooltips[index] = (prefix == null ? "" : prefix + ": ") + UiUtil.itemLabel(info.name, qty, info.stackable);
			repaint();
		});
	}

	private int originX()
	{
		return (getWidth() - (2 * COL_PITCH + TILE)) / 2;
	}

	private int cx(int col)
	{
		return originX() + col * COL_PITCH + TILE / 2;
	}

	private static int cy(int row)
	{
		return 4 + row * ROW_PITCH + TILE / 2;
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0;
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());

		if (equipment == null)
		{
			UiUtil.drawShadowedCentered(g, "Not shared", getWidth() / 2, getHeight() / 2, UiUtil.RS_ORANGE);
			return;
		}

		// Connecting bars, drawn under the tiles.
		g.setColor(UiUtil.RS_BAR);
		vbar(g, 1, 0, 4);
		hbar(g, 1, 0, 2);
		hbar(g, 2, 0, 2);
		vbar(g, 0, 2, 4);
		vbar(g, 2, 2, 4);

		BufferedImage tile = sprites.peek(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE, this::repaint);
		for (Object[] s : SLOTS)
		{
			EquipmentInventorySlot slot = (EquipmentInventorySlot) s[0];
			int x = cx((int) s[1]) - TILE / 2;
			int y = cy((int) s[2]) - TILE / 2;
			drawTile(g, tile, x, y);
			BufferedImage item = slotImages[slot.getSlotIdx()];
			if (item != null)
			{
				g.drawImage(item, x + (TILE - item.getWidth()) / 2, y + (TILE - item.getHeight()) / 2, null);
			}
			else
			{
				BufferedImage ph = sprites.peek((int) s[3], this::repaint);
				if (ph != null)
				{
					g.drawImage(ph, x + (TILE - ph.getWidth()) / 2, y + (TILE - ph.getHeight()) / 2, null);
				}
			}
		}

		if (quiver != null)
		{
			int x = cx(QUIVER_COL) - TILE / 2;
			int y = cy(QUIVER_ROW) - TILE / 2;
			drawTile(g, tile, x, y);
			if (quiverImage != null)
			{
				g.drawImage(quiverImage, x + (TILE - quiverImage.getWidth()) / 2, y + (TILE - quiverImage.getHeight()) / 2, null);
			}
		}
	}

	private static void drawTile(Graphics2D g, BufferedImage tile, int x, int y)
	{
		if (tile != null)
		{
			g.drawImage(tile, x, y, TILE, TILE, null);
		}
		else
		{
			g.setColor(UiUtil.RS_SLOT);
			g.fillRect(x, y, TILE, TILE);
		}
	}

	private void vbar(Graphics2D g, int col, int fromRow, int toRow)
	{
		g.fillRect(cx(col) - BAR / 2, cy(fromRow), BAR, cy(toRow) - cy(fromRow));
	}

	private void hbar(Graphics2D g, int row, int fromCol, int toCol)
	{
		g.fillRect(cx(fromCol), cy(row) - BAR / 2, cx(toCol) - cx(fromCol), BAR);
	}

	@Override
	public String getToolTipText(java.awt.event.MouseEvent e)
	{
		for (Object[] s : SLOTS)
		{
			if (hit(e, (int) s[1], (int) s[2]))
			{
				return tooltips[((EquipmentInventorySlot) s[0]).getSlotIdx()];
			}
		}
		if (quiver != null && hit(e, QUIVER_COL, QUIVER_ROW))
		{
			return tooltips[14];
		}
		return null;
	}

	private boolean hit(java.awt.event.MouseEvent e, int col, int row)
	{
		return Math.abs(e.getX() - cx(col)) <= TILE / 2 && Math.abs(e.getY() - cy(row)) <= TILE / 2;
	}
}
