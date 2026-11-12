package com.runebeacon;

import com.runebeacon.model.Group;
import com.runebeacon.model.Member;
import com.runebeacon.model.MemberState;
import com.runebeacon.ui.UiUtil;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Keeps one world map pin per visible group member. Desired pins are computed on the EDT from
 * {@link GroupStore}; changes are applied to the WorldMapPointManager on the client thread.
 */
@Singleton
class MapPins
{
	private static final class Pin
	{
		final WorldMapPoint point;
		String label;
		boolean online;

		Pin(WorldMapPoint point)
		{
			this.point = point;
		}
	}

	private static final class Desired
	{
		String userId;
		WorldPoint wp;
		String label;
		String tooltip;
		boolean online;
		Color color;
	}

	private final WorldMapPointManager worldMapPointManager;
	private final ClientThread clientThread;
	private final GroupStore store;
	private final RuneBeaconConfig config;

	/** Client-thread confined. */
	private final Map<String, Pin> pins = new HashMap<>();

	@Inject
	MapPins(WorldMapPointManager worldMapPointManager, ClientThread clientThread, GroupStore store, RuneBeaconConfig config)
	{
		this.worldMapPointManager = worldMapPointManager;
		this.clientThread = clientThread;
		this.store = store;
		this.config = config;
	}

	/** Recompute pins. Call on the EDT. */
	void refresh(String selectedGroupId)
	{
		List<Desired> desired = new ArrayList<>();
		if (config.showMapPins())
		{
			Collection<Group> source;
			if (config.mapPinsAllGroups())
			{
				source = store.getGroups();
			}
			else
			{
				Group g = store.getGroup(selectedGroupId);
				source = g == null ? new ArrayList<>() : java.util.Collections.singletonList(g);
			}

			for (Member m : store.distinctMembers(source).values())
			{
				if (m.getUserId().equals(store.getSelfUserId()))
				{
					continue;
				}
				if (!m.isOnline() && !config.showOfflineOnMap())
				{
					continue;
				}
				MemberState s = store.getState(m.getUserId());
				if (s == null || s.getLoc() == null || UiUtil.isHidden(s))
				{
					continue;
				}
				Desired d = new Desired();
				d.userId = m.getUserId();
				d.wp = new WorldPoint(s.getLoc().getX(), s.getLoc().getY(), s.getLoc().getPlane());
				d.online = m.isOnline();
				d.label = m.getRsn() + (d.online && s.getWorld() != null ? "  W" + s.getWorld() : "");
				d.tooltip = UiUtil.describe(m, s);
				d.color = UiUtil.memberColor(m.getUserId());
				desired.add(d);
			}
		}

		clientThread.invokeLater(() -> apply(desired));
	}

	void clear()
	{
		clientThread.invokeLater(() ->
		{
			for (Pin p : pins.values())
			{
				worldMapPointManager.remove(p.point);
			}
			pins.clear();
		});
	}

	private void apply(List<Desired> desired)
	{
		Set<String> keep = new HashSet<>();
		for (Desired d : desired)
		{
			keep.add(d.userId);
			Pin pin = pins.get(d.userId);
			if (pin == null)
			{
				BufferedImage img = render(d.label, d.color, d.online);
				WorldMapPoint point = WorldMapPoint.builder()
					.worldPoint(d.wp)
					.image(img)
					.imagePoint(anchor(img))
					.name(d.label)
					.tooltip(d.tooltip)
					.snapToEdge(true)
					.jumpOnClick(true)
					.build();
				pin = new Pin(point);
				pin.label = d.label;
				pin.online = d.online;
				pins.put(d.userId, pin);
				worldMapPointManager.add(point);
				continue;
			}
			pin.point.setWorldPoint(d.wp);
			pin.point.setTarget(d.wp);
			pin.point.setTooltip(d.tooltip);
			if (!Objects.equals(pin.label, d.label) || pin.online != d.online)
			{
				pin.label = d.label;
				pin.online = d.online;
				pin.point.setName(d.label);
				BufferedImage img = render(d.label, d.color, d.online);
				pin.point.setImage(img);
				pin.point.setImagePoint(anchor(img));
			}
		}
		pins.entrySet().removeIf(e ->
		{
			if (!keep.contains(e.getKey()))
			{
				worldMapPointManager.remove(e.getValue().point);
				return true;
			}
			return false;
		});
	}

	/** The pin sits on the centre of the dot, not the centre of the whole label. */
	private static net.runelite.api.Point anchor(BufferedImage img)
	{
		return new net.runelite.api.Point(5, img.getHeight() / 2);
	}

	/** A coloured dot with a name/world label to its right. */
	private static BufferedImage render(String label, Color color, boolean online)
	{
		final int dot = 10;
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		pg.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = pg.getFontMetrics();
		pg.dispose();

		int textW = fm.stringWidth(label);
		int w = dot + 4 + textW + 6;
		int h = Math.max(dot, fm.getHeight()) + 4;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());

		Color fill = online ? color : Color.GRAY;
		int dy = (h - dot) / 2;
		g.setColor(Color.BLACK);
		g.fillOval(0, dy, dot, dot);
		g.setColor(fill);
		g.fillOval(1, dy + 1, dot - 2, dot - 2);

		g.setColor(new Color(0, 0, 0, 160));
		g.fillRoundRect(dot + 2, 0, textW + 6, h, 6, 6);
		int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
		g.setColor(Color.BLACK);
		g.drawString(label, dot + 6, ty + 1);
		g.setColor(online ? Color.WHITE : Color.LIGHT_GRAY);
		g.drawString(label, dot + 5, ty);
		g.dispose();
		return img;
	}
}
