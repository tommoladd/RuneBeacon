package com.runebeacon.ui;

import com.runebeacon.model.MemberState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.FontManager;

/**
 * At-a-glance status on the in-game brown background: prayer (points + active prayers),
 * spellbook, special attack, run energy, weight, Vengeance, poison and the rune pouch.
 */
class StatusPanel extends JPanel
{
	private static final Map<String, Integer> PRAYER_SPRITES = new HashMap<>();

	static
	{
		PRAYER_SPRITES.put("THICK_SKIN", SpriteID.Prayeron.THICK_SKIN);
		PRAYER_SPRITES.put("BURST_OF_STRENGTH", SpriteID.Prayeron.BURST_OF_STRENGTH);
		PRAYER_SPRITES.put("CLARITY_OF_THOUGHT", SpriteID.Prayeron.CLARITY_OF_THOUGHT);
		PRAYER_SPRITES.put("SHARP_EYE", SpriteID.Prayeron.SHARP_EYE);
		PRAYER_SPRITES.put("MYSTIC_WILL", SpriteID.Prayeron.MYSTIC_WILL);
		PRAYER_SPRITES.put("ROCK_SKIN", SpriteID.Prayeron.ROCK_SKIN);
		PRAYER_SPRITES.put("SUPERHUMAN_STRENGTH", SpriteID.Prayeron.SUPERHUMAN_STRENGTH);
		PRAYER_SPRITES.put("IMPROVED_REFLEXES", SpriteID.Prayeron.IMPROVED_REFLEXES);
		PRAYER_SPRITES.put("RAPID_RESTORE", SpriteID.Prayeron.RAPID_RESTORE);
		PRAYER_SPRITES.put("RAPID_HEAL", SpriteID.Prayeron.RAPID_HEAL);
		PRAYER_SPRITES.put("PROTECT_ITEM", SpriteID.Prayeron.PROTECT_ITEM);
		PRAYER_SPRITES.put("HAWK_EYE", SpriteID.Prayeron.HAWK_EYE);
		PRAYER_SPRITES.put("MYSTIC_LORE", SpriteID.Prayeron.MYSTIC_LORE);
		PRAYER_SPRITES.put("STEEL_SKIN", SpriteID.Prayeron.STEEL_SKIN);
		PRAYER_SPRITES.put("ULTIMATE_STRENGTH", SpriteID.Prayeron.ULTIMATE_STRENGTH);
		PRAYER_SPRITES.put("INCREDIBLE_REFLEXES", SpriteID.Prayeron.INCREDIBLE_REFLEXES);
		PRAYER_SPRITES.put("PROTECT_FROM_MAGIC", SpriteID.Prayeron.PROTECT_FROM_MAGIC);
		PRAYER_SPRITES.put("PROTECT_FROM_MISSILES", SpriteID.Prayeron.PROTECT_FROM_MISSILES);
		PRAYER_SPRITES.put("PROTECT_FROM_MELEE", SpriteID.Prayeron.PROTECT_FROM_MELEE);
		PRAYER_SPRITES.put("EAGLE_EYE", SpriteID.Prayeron.EAGLE_EYE);
		PRAYER_SPRITES.put("MYSTIC_MIGHT", SpriteID.Prayeron.MYSTIC_MIGHT);
		PRAYER_SPRITES.put("RETRIBUTION", SpriteID.Prayeron.RETRIBUTION);
		PRAYER_SPRITES.put("REDEMPTION", SpriteID.Prayeron.REDEMPTION);
		PRAYER_SPRITES.put("SMITE", SpriteID.Prayeron.SMITE);
		PRAYER_SPRITES.put("PRESERVE", SpriteID.Prayeron.PRESERVE);
		PRAYER_SPRITES.put("CHIVALRY", SpriteID.Prayeron.CHIVALRY);
		PRAYER_SPRITES.put("DEADEYE", SpriteID.Prayeron.DEADEYE);
		PRAYER_SPRITES.put("MYSTIC_VIGOUR", SpriteID.Prayeron.MYSTIC_VIGOUR);
		PRAYER_SPRITES.put("PIETY", SpriteID.Prayeron.PIETY);
		PRAYER_SPRITES.put("RIGOUR", SpriteID.Prayeron.RIGOUR);
		PRAYER_SPRITES.put("AUGURY", SpriteID.Prayeron.AUGURY);
		PRAYER_SPRITES.put("ZEAL", SpriteID.Prayeron.ZEAL);
	}

	private final PanelServices services;

	private final StatRow prayerRow;
	private final JPanel prayerIcons = flow();
	private final StatRow spellbookRow;
	private final StatRow specRow;
	private final StatRow runRow;
	private final StatRow weightRow;
	private final StatRow vengeanceRow;
	private final StatRow poisonRow;
	private final JLabel pouchTitle = title("Rune pouch");
	private final JPanel pouchRow = flow();
	private final JLabel notShared = text("Not shared", UiUtil.RS_ORANGE);
	private String shownSpellbook;

	StatusPanel(PanelServices services)
	{
		this.services = services;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(UiUtil.RS_BROWN);
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		prayerRow = new StatRow(SpriteID.OrbIcon.PRAYER, "Prayer");
		spellbookRow = new StatRow(SpriteID.SideIcons.MAGIC, "Spellbook");
		specRow = new StatRow(SpriteID.OrbIcon.SPECIAL, "Special attack");
		runRow = new StatRow(SpriteID.OrbIcon.RUN, "Run energy");
		weightRow = new StatRow(-1, "Weight");
		vengeanceRow = new StatRow(-1, "Vengeance");
		poisonRow = new StatRow(SpriteID.OrbIcon.HITPOINTS, "Poison");

		add(notShared);
		add(prayerRow);
		add(prayerIcons);
		add(spellbookRow);
		add(specRow);
		add(runRow);
		add(weightRow);
		add(vengeanceRow);
		add(poisonRow);
		add(pouchTitle);
		add(pouchRow);
	}

	void setState(MemberState s)
	{
		boolean vitals = s != null && s.getHp() != null;
		notShared.setVisible(!vitals && (s == null || s.getPrayers() == null));

		prayerRow.setVisible(vitals && s.getPrayer() != null);
		if (vitals && s.getPrayer() != null)
		{
			int[] p = s.getPrayer();
			prayerRow.setValue(p[0] + " / " + p[1], UiUtil.levelColor(p[0], p[1]));
		}
		setPrayers(s == null ? null : s.getPrayers());

		String book = s == null ? null : s.getSpellbook();
		spellbookRow.setVisible(book != null);
		if (book != null && !book.equals(shownSpellbook))
		{
			shownSpellbook = book;
			spellbookRow.setIcon(spellbookSprite(book));
			spellbookRow.setValue(spellbookName(book), UiUtil.RS_YELLOW);
		}

		specRow.setVisible(vitals && s.getSpec() != null);
		if (vitals && s.getSpec() != null)
		{
			specRow.setValue(s.getSpec() + "%", UiUtil.levelColor(s.getSpec(), 100));
		}

		runRow.setVisible(vitals && s.getRun() != null);
		if (vitals && s.getRun() != null)
		{
			boolean stam = Boolean.TRUE.equals(s.getStamina());
			runRow.setValue(s.getRun() + "%" + (stam ? "  (stamina)" : ""), UiUtil.levelColor(s.getRun(), 100));
		}

		weightRow.setVisible(vitals && s.getWeight() != null);
		if (vitals && s.getWeight() != null)
		{
			weightRow.setValue(s.getWeight() + " kg", UiUtil.RS_YELLOW);
		}

		boolean veng = vitals && Boolean.TRUE.equals(s.getVengeance());
		vengeanceRow.setVisible(veng);
		if (veng)
		{
			vengeanceRow.setValue("Active", UiUtil.RS_ORANGE);
		}

		String poison = s == null ? null : s.getPoison();
		boolean poisoned = vitals && ("poison".equals(poison) || "venom".equals(poison));
		poisonRow.setVisible(poisoned);
		if (poisoned)
		{
			boolean venom = "venom".equals(poison);
			poisonRow.setLabel(venom ? "Venom" : "Poison");
			poisonRow.setValue(venom ? "Venomed" : "Poisoned", venom ? UiUtil.RS_VENOM : UiUtil.RS_POISON);
		}

		setPouch(s == null ? null : s.getRunePouch());
		revalidate();
		repaint();
	}

	private List<String> shownPrayers;
	private int[] shownPouch;
	private boolean prayersInit;
	private boolean pouchInit;

	private void setPrayers(List<String> prayers)
	{
		if (prayersInit && java.util.Objects.equals(prayers, shownPrayers))
		{
			return;
		}
		prayersInit = true;
		shownPrayers = prayers;
		prayerIcons.removeAll();
		prayerIcons.setVisible(prayers != null && !prayers.isEmpty());
		if (prayers == null)
		{
			return;
		}
		for (String p : prayers)
		{
			Integer sprite = PRAYER_SPRITES.get(p);
			String name = UiUtil.titleCase(p.startsWith("RP_") ? p.substring(3) : p);
			JLabel l = new JLabel();
			l.setToolTipText(name);
			if (sprite == null)
			{
				// Ruinous Powers and anything without a mapped icon: show the name.
				l.setText(name);
				l.setFont(FontManager.getRunescapeSmallFont());
				l.setForeground(UiUtil.RS_ORANGE);
			}
			else
			{
				l.setPreferredSize(new Dimension(30, 30));
				l.setHorizontalAlignment(SwingConstants.CENTER);
				services.getSprites().get(sprite, img -> l.setIcon(new ImageIcon(img)));
			}
			prayerIcons.add(l);
		}
	}

	private void setPouch(int[] pouch)
	{
		if (prayersInit && pouchInit && java.util.Arrays.equals(pouch, shownPouch))
		{
			return;
		}
		pouchInit = true;
		shownPouch = pouch;
		pouchRow.removeAll();
		boolean hasPouch = pouch != null && pouch.length >= 2;
		pouchTitle.setVisible(hasPouch);
		pouchRow.setVisible(hasPouch);
		if (hasPouch)
		{
			for (int i = 0; i + 1 < pouch.length; i += 2)
			{
				JLabel cell = new JLabel();
				cell.setPreferredSize(new Dimension(36, 32));
				cell.setHorizontalAlignment(SwingConstants.CENTER);
				ItemGrid.showItem(services, cell, pouch[i], pouch[i + 1], null);
				pouchRow.add(cell);
			}
		}
	}

	private static int spellbookSprite(String book)
	{
		switch (book)
		{
			case "ancient":
				return SpriteID.SideIcons.SPELLBOOK_ANCIENT_MAGICKS;
			case "lunar":
				return SpriteID.SideIcons.SPELLBOOK_LUNAR;
			case "arceuus":
				return SpriteID.SideIcons.SPELLBOOK_ARCEUUS;
			default:
				return SpriteID.SideIcons.MAGIC;
		}
	}

	private static String spellbookName(String book)
	{
		switch (book)
		{
			case "ancient":
				return "Ancient Magicks";
			case "lunar":
				return "Lunar";
			case "arceuus":
				return "Arceuus";
			default:
				return "Standard";
		}
	}

	/** Icon + label on the left, value on the right. */
	private final class StatRow extends JPanel
	{
		private final JLabel label;
		private final JLabel value = text("", UiUtil.RS_YELLOW);

		StatRow(int sprite, String name)
		{
			super(new BorderLayout(6, 0));
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
			setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
			label = text(name, UiUtil.RS_ORANGE);
			label.setIconTextGap(6);
			label.setPreferredSize(new Dimension(110, 22));
			if (sprite >= 0)
			{
				setIcon(sprite);
			}
			else
			{
				// Keep text aligned with rows that have an icon.
				label.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
			}
			value.setHorizontalAlignment(SwingConstants.RIGHT);
			add(label, BorderLayout.WEST);
			add(value, BorderLayout.CENTER);
		}

		void setIcon(int sprite)
		{
			services.getSprites().get(sprite, img -> label.setIcon(new ImageIcon(
				img.getHeight() > 18 ? img.getScaledInstance(-1, 18, java.awt.Image.SCALE_SMOOTH) : img)));
		}

		void setLabel(String text)
		{
			label.setText(text);
		}

		void setValue(String text, Color color)
		{
			value.setText(text);
			value.setForeground(color);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private static JPanel flow()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel title(String t)
	{
		JLabel l = text(t, UiUtil.RS_ORANGE);
		l.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 0));
		return l;
	}

	private static JLabel text(String t, Color color)
	{
		JLabel l = new JLabel(t);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
