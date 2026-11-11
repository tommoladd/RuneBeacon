package com.runebeacon.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.FontManager;

/**
 * The 28-slot inventory on the in-game brown background; items keep their real slots.
 * Right-click an item to ping it; incoming item pings make the slot flash.
 */
class ItemGrid extends JPanel
{
	interface PingHandler
	{
		void ping(int slot, int itemId, String itemName);
	}

	private static final int SLOTS = 28;

	private final PanelServices services;
	private final SlotLabel[] cells = new SlotLabel[SLOTS];
	private PingHandler pingHandler;
	private int[] items;

	ItemGrid(PanelServices services)
	{
		super(new GridLayout(7, 4, 2, 2));
		this.services = services;
		setBackground(UiUtil.RS_BROWN);
		setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
	}

	void setPingHandler(PingHandler handler)
	{
		this.pingHandler = handler;
	}

	void setItems(int[] items)
	{
		this.items = items;
		removeAll();
		java.util.Arrays.fill(cells, null);
		if (items == null)
		{
			setLayout(new GridLayout(1, 1));
			JLabel l = new JLabel("Not shared", SwingConstants.CENTER);
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(UiUtil.RS_ORANGE);
			l.setPreferredSize(new Dimension(0, 60));
			add(l);
		}
		else
		{
			setLayout(new GridLayout(7, 4, 2, 2));
			for (int i = 0; i < SLOTS; i++)
			{
				SlotLabel cell = new SlotLabel(i);
				if (i * 2 + 1 < items.length && items[i * 2] > -1)
				{
					showItem(services, cell, items[i * 2], items[i * 2 + 1], null);
				}
				cells[i] = cell;
				add(cell);
			}
		}
		revalidate();
		repaint();
	}

	/** Briefly outline a slot in the pinger's colour. */
	void flash(int slot, Color color, int durationMs)
	{
		if (slot >= 0 && slot < SLOTS && cells[slot] != null)
		{
			cells[slot].flash(color, durationMs);
		}
	}

	/** Item picture plus a hover tooltip like "Shark" or "Quiver: Amethyst arrow x500". */
	static void showItem(PanelServices services, JLabel cell, int itemId, int qty, String prefix)
	{
		services.getItemInfo().lookup(itemId, info ->
		{
			services.getItemManager().getImage(itemId, qty, info.stackable).addTo(cell);
			String text = UiUtil.itemLabel(info.name, qty, info.stackable);
			cell.setToolTipText(prefix == null ? text : prefix + ": " + text);
			cell.setName(info.name);
		});
	}

	private final class SlotLabel extends JLabel
	{
		private final int slot;
		private Color flashColor;
		private Timer flashTimer;

		SlotLabel(int slot)
		{
			this.slot = slot;
			setPreferredSize(new Dimension(36, 32));
			setHorizontalAlignment(SwingConstants.CENTER);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					maybeMenu(e);
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (e.getButton() == MouseEvent.BUTTON1 && services.getPingSettings().isPingClick(e))
					{
						pingNow();
						return;
					}
					maybeMenu(e);
				}
			});
		}

		/** Hotkey + click: ping this slot straight away. */
		private void pingNow()
		{
			if (pingHandler == null || items == null || slot * 2 >= items.length || items[slot * 2] < 0)
			{
				return;
			}
			pingHandler.ping(slot, items[slot * 2], getName() == null ? "item" : getName());
		}

		private void maybeMenu(MouseEvent e)
		{
			if (!e.isPopupTrigger() || pingHandler == null || items == null || slot * 2 >= items.length || items[slot * 2] < 0)
			{
				return;
			}
			int itemId = items[slot * 2];
			String itemName = getName() == null ? "item" : getName();
			JPopupMenu menu = new JPopupMenu();
			JMenuItem ping = new JMenuItem("Ping " + itemName);
			ping.addActionListener(a -> pingHandler.ping(slot, itemId, itemName));
			menu.add(ping);
			menu.show(this, e.getX(), e.getY());
		}

		void flash(Color color, int durationMs)
		{
			flashColor = color;
			repaint();
			if (flashTimer != null)
			{
				flashTimer.stop();
			}
			flashTimer = new Timer(durationMs, e ->
			{
				flashColor = null;
				repaint();
			});
			flashTimer.setRepeats(false);
			flashTimer.start();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (flashColor != null)
			{
				Graphics2D g2 = (Graphics2D) g;
				g2.setColor(new Color(flashColor.getRed(), flashColor.getGreen(), flashColor.getBlue(), 60));
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.setColor(flashColor);
				g2.setStroke(new BasicStroke(2));
				g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
			}
		}
	}
}
