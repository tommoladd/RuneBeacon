package com.runebeacon;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines inventory slots that a group member pinged, pulsing and fading out over the ping
 * duration. Slot positions come straight from the inventory interface (child index = slot).
 */
@Singleton
class InventoryPingOverlay extends Overlay
{
	@Value
	static class SlotPing
	{
		Color color;
		long createdAt;
		long durationMs;
	}

	private final Client client;
	private final ItemManager itemManager;
	/** slot -> latest ping */
	private final Map<Integer, SlotPing> pings = new ConcurrentHashMap<>();

	@Inject
	InventoryPingOverlay(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** Thread-safe. */
	void add(int slot, SlotPing ping)
	{
		pings.put(slot, ping);
	}

	void clear()
	{
		pings.clear();
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (pings.isEmpty())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		pings.entrySet().removeIf(e -> now - e.getValue().getCreatedAt() > e.getValue().getDurationMs());

		Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (inventory == null || inventory.isHidden())
		{
			return null;
		}
		for (Map.Entry<Integer, SlotPing> e : pings.entrySet())
		{
			Widget slot = inventory.getChild(e.getKey());
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			draw(g, slot, e.getValue(), now);
		}
		return null;
	}

	private void draw(Graphics2D g, Widget slot, SlotPing ping, long now)
	{
		long age = now - ping.getCreatedAt();
		// Pulse twice a second, fading out over the last half of the duration.
		float life = (float) age / ping.getDurationMs();
		float fade = life < 0.5f ? 1f : Math.max(0f, (1f - life) * 2f);
		float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(age / 1000.0 * Math.PI * 2));
		int alpha = Math.min(255, (int) (255 * fade * pulse));
		Color c = ping.getColor();
		Rectangle b = slot.getBounds();

		int itemId = slot.getItemId();
		if (itemId > 0)
		{
			// Solid-colour outline (cacheable by ItemManager), faded with a composite.
			BufferedImage outline = itemManager.getItemOutline(itemId, slot.getItemQuantity(), c);
			Composite old = g.getComposite();
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
			g.drawImage(outline, b.x + (b.width - outline.getWidth()) / 2, b.y + (b.height - outline.getHeight()) / 2, null);
			g.setComposite(old);
		}
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha / 5));
		g.fillRect(b.x, b.y, b.width, b.height);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
		g.setStroke(new BasicStroke(2));
		g.drawRect(b.x, b.y, b.width - 1, b.height - 1);
	}
}
