package com.runebeacon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Draws recent tile pings from group members: a fading tile outline with the pinger's name. */
@Singleton
class TilePingOverlay extends Overlay
{
	private static final int MAX_PINGS = 20;

	@Value
	static class Ping
	{
		WorldPoint point;
		String name;
		Color color;
		long createdAt;
		long durationMs;
	}

	private final Client client;
	private final List<Ping> pings = new CopyOnWriteArrayList<>();

	@Inject
	TilePingOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Thread-safe. */
	void add(Ping ping)
	{
		pings.add(ping);
		while (pings.size() > MAX_PINGS)
		{
			pings.remove(0);
		}
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
		pings.removeIf(p -> now - p.getCreatedAt() > p.getDurationMs());

		for (Ping p : pings)
		{
			if (p.getPoint().getPlane() != client.getTopLevelWorldView().getPlane())
			{
				continue;
			}
			LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), p.getPoint());
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly == null)
			{
				continue;
			}
			// Full strength for the first half, then fade out.
			float life = (float) (now - p.getCreatedAt()) / p.getDurationMs();
			int alpha = (int) (255 * Math.max(0f, Math.min(1f, life < 0.5f ? 1f : (1f - life) * 2f)));
			Color c = p.getColor();
			OverlayUtil.renderPolygon(g, poly,
				new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha),
				new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha / 4),
				new BasicStroke(3));

			if (p.getName() == null)
			{
				continue;
			}
			g.setFont(FontManager.getRunescapeSmallFont());
			Point text = Perspective.getCanvasTextLocation(client, g, lp, p.getName(), 0);
			if (text != null)
			{
				OverlayUtil.renderTextLocation(g, text, p.getName(), new Color(255, 255, 255, alpha));
			}
		}
		return null;
	}
}
