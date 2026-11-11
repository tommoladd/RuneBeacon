package com.runebeacon.ui;

import com.runebeacon.model.Group;
import com.runebeacon.model.Member;
import com.runebeacon.model.MemberState;
import com.runebeacon.model.Permission;
import com.runebeacon.model.Permissions;
import com.runebeacon.model.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One group member, styled after the in-game interfaces: name/world header, orb-style vitals row,
 * and (when expanded) icon tabs for inventory, equipment, prayers and skills.
 */
class MemberCard extends JPanel
{
	interface Actions
	{
		void kick(Group group, Member member);

		void editPermissions(Group group, Member member);

		void transfer(Group group, Member member);

		void pingItem(Group group, Member member, int slot, int itemId, String itemName);

		void setPingsHidden(Member member, boolean hidden);
	}

	private enum Tab
	{
		INVENTORY(SpriteID.SideIcons.INVENTORY, "Inventory"),
		EQUIPMENT(SpriteID.SideIcons.EQUIPMENT, "Equipment"),
		STATUS(SpriteID.SideiconsInterface.CHARACTER_SUMMARY, "Status: prayer, spellbook, energy, weight"),
		SKILLS(SpriteID.SideIcons.STATS, "Skills");

		final int sprite;
		final String tooltip;

		Tab(int sprite, String tooltip)
		{
			this.sprite = sprite;
			this.tooltip = tooltip;
		}
	}

	private static final int ORB_ICON = 15;

	private final PanelServices services;
	private final Actions actions;
	private Group group;
	private Member member;
	private MemberState state;
	private final String selfUserId;
	private final boolean isSelf;

	private static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;

	private final JLabel dot = new JLabel("●");
	private final JLabel name = new JLabel();
	private final JLabel presence = new JLabel();
	private final JLabel chevron = new JLabel("▸");
	private final JLabel doing = new JLabel();
	private final JLabel status = new JLabel();
	private final JPanel orbs = new JPanel(new GridLayout(1, 4, 2, 0));
	private final JLabel hpOrb = orbLabel();
	private final JLabel prayerOrb = orbLabel();
	private final JLabel specOrb = orbLabel();
	private final JLabel runOrb = orbLabel();

	private final JPanel details = new JPanel(new BorderLayout());
	private final JPanel tabStrip = new JPanel(new GridLayout(1, Tab.values().length, 2, 0));
	private final TabButton[] tabButtons = new TabButton[Tab.values().length];
	/** Holds only the visible page, so each tab is its own height (no empty space under short tabs). */
	private final JPanel pageHolder = new JPanel(new BorderLayout());
	private final ItemGrid inventoryPage;
	private final GearPanel gearPage;
	private final StatusPanel statusPage;
	private final SkillsPanel skillsPage;
	private Tab currentTab = Tab.INVENTORY;
	private boolean expanded;

	// Last rendered detail data, to skip rebuilding unchanged pages.
	private boolean detailsShown;
	private int[] shownInventory;
	private int[] shownEquipment;
	private int[] shownQuiver;
	private Map<String, long[]> shownSkills;

	MemberCard(PanelServices services, Actions actions, Group group, Member member, String selfUserId)
	{
		this.services = services;
		this.actions = actions;
		this.group = group;
		this.member = member;
		this.selfUserId = selfUserId;
		this.isSelf = member.getUserId().equals(selfUserId);

		setLayout(new BorderLayout());
		setBackground(CARD_BG);
		// Left stripe in the member's colour, matching their world map pin.
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, UiUtil.memberColor(member.getUserId())),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		dot.setFont(FontManager.getRunescapeSmallFont());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		presence.setFont(FontManager.getRunescapeSmallFont());
		presence.setHorizontalAlignment(SwingConstants.RIGHT);
		chevron.setFont(FontManager.getRunescapeSmallFont());
		chevron.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel east = new JPanel(new BorderLayout(4, 0));
		east.setOpaque(false);
		east.add(presence, BorderLayout.CENTER);
		east.add(chevron, BorderLayout.EAST);
		header.add(dot, BorderLayout.WEST);
		header.add(name, BorderLayout.CENTER);
		header.add(east, BorderLayout.EAST);
		body.add(row(header));

		doing.setFont(FontManager.getRunescapeSmallFont());
		doing.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(row(doing));

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(UiUtil.RS_ORANGE);
		body.add(row(status));

		orbs.setOpaque(false);
		orbs.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		orbs.add(hpOrb);
		orbs.add(prayerOrb);
		orbs.add(specOrb);
		orbs.add(runOrb);
		setOrbIcon(hpOrb, SpriteID.OrbIcon.HITPOINTS);
		setOrbIcon(prayerOrb, SpriteID.OrbIcon.PRAYER);
		setOrbIcon(specOrb, SpriteID.OrbIcon.SPECIAL);
		setOrbIcon(runOrb, SpriteID.OrbIcon.RUN);
		hpOrb.setToolTipText("Hitpoints");
		prayerOrb.setToolTipText("Prayer");
		specOrb.setToolTipText("Special attack");
		body.add(row(orbs));
		add(body, BorderLayout.CENTER);

		// Detail tabs
		inventoryPage = new ItemGrid(services);
		inventoryPage.setPingHandler((slot, itemId, itemName) -> actions.pingItem(this.group, this.member, slot, itemId, itemName));
		gearPage = new GearPanel(services.getSprites(), services.getItemManager(), services.getItemInfo());
		statusPage = new StatusPanel(services);
		skillsPage = new SkillsPanel(services.getSprites(), services.getSkillIcons());

		tabStrip.setOpaque(false);
		for (Tab t : Tab.values())
		{
			TabButton b = new TabButton(t);
			tabButtons[t.ordinal()] = b;
			tabStrip.add(b);
		}
		details.setOpaque(false);
		details.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		details.add(tabStrip, BorderLayout.NORTH);
		JPanel pageWrap = new JPanel(new BorderLayout());
		pageWrap.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR));
		pageWrap.add(pageHolder, BorderLayout.CENTER);
		details.add(pageWrap, BorderLayout.CENTER);
		details.setVisible(false);
		add(details, BorderLayout.SOUTH);
		selectTab(Tab.INVENTORY);

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					showMenu(e);
				}
				else if (e.getButton() == MouseEvent.BUTTON1)
				{
					setExpanded(!expanded);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				// Moving between child labels fires exit/enter pairs; only reset when truly outside.
				java.awt.Point p = javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), body);
				if (!body.contains(p))
				{
					setBackground(CARD_BG);
				}
			}
		};
		// Labels with tooltips swallow mouse events, so listen on every child, not just the body.
		addMouseListenerDeep(body, mouse);
		body.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private static void addMouseListenerDeep(java.awt.Component c, MouseAdapter l)
	{
		c.addMouseListener(l);
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				addMouseListenerDeep(child, l);
			}
		}
	}

	private static JPanel row(java.awt.Component c)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.add(c, BorderLayout.CENTER);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		return wrap;
	}

	private static JLabel orbLabel()
	{
		JLabel l = new JLabel("-");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(UiUtil.RS_YELLOW);
		l.setIconTextGap(3);
		return l;
	}

	private void setOrbIcon(JLabel label, int sprite)
	{
		services.getSprites().get(sprite, img -> label.setIcon(new ImageIcon(scaleTo(img, ORB_ICON))));
	}

	private static Image scaleTo(java.awt.image.BufferedImage img, int max)
	{
		int w = img.getWidth();
		int h = img.getHeight();
		if (w <= max && h <= max)
		{
			return img;
		}
		double s = (double) max / Math.max(w, h);
		return img.getScaledInstance((int) Math.round(w * s), (int) Math.round(h * s), Image.SCALE_SMOOTH);
	}

	String getUserId()
	{
		return member.getUserId();
	}

	/** An item ping for this member: flash the slot (only visible while the card is expanded). */
	void flashInventorySlot(int slot, Color color, int durationMs)
	{
		inventoryPage.flash(slot, color, durationMs);
	}

	void setMember(Group group, Member member)
	{
		this.group = group;
		this.member = member;
	}

	void update(MemberState s)
	{
		this.state = s;
		boolean loggedIn = UiUtil.isLoggedIn(member, s);
		dot.setForeground(loggedIn ? UiUtil.presenceColor(member, s) : UiUtil.OFFLINE);

		Set<Permission> perms = Permissions.of(member.getPermissions());
		boolean owner = member.getRole() == Role.owner;
		String roleTag = owner ? " ★" : perms.isEmpty() ? "" : " ☆";
		name.setText(member.getRsn() + roleTag);
		String who = isSelf ? "You" : null;
		String role = owner ? "Group owner" : perms.isEmpty() ? null : "Permissions: " + describe(perms);
		name.setToolTipText(who == null ? role : role == null ? who : who + " - " + role);

		presence.setText(UiUtil.presenceText(member, s));
		presence.setForeground(UiUtil.presenceColor(member, s));

		// Second line: "Lvl 126 · Fighting Vorkath"
		String doingText = UiUtil.doing(s);
		String level = s != null && s.getCombatLevel() != null && loggedIn ? "Lvl " + s.getCombatLevel() : null;
		String line = level == null ? doingText : doingText == null ? level : level + " · " + doingText;
		doing.setText(line == null ? "" : line);
		doing.setVisible(line != null);
		String statusText = s == null ? null : s.getStatus();
		status.setText(statusText == null ? "" : "“" + statusText + "”");
		status.setVisible(statusText != null);

		boolean showOrbs = loggedIn && s.getHp() != null;
		orbs.setVisible(showOrbs);
		if (showOrbs)
		{
			int[] hp = s.getHp();
			hpOrb.setText(String.valueOf(hp[0]));
			Color hpColor = "venom".equals(s.getPoison()) ? UiUtil.RS_VENOM
				: "poison".equals(s.getPoison()) ? UiUtil.RS_POISON
				: UiUtil.levelColor(hp[0], hp[1]);
			hpOrb.setForeground(hpColor);
			hpOrb.setToolTipText("Hitpoints " + hp[0] + "/" + hp[1]
				+ ("venom".equals(s.getPoison()) ? " (venomed)" : "poison".equals(s.getPoison()) ? " (poisoned)" : ""));
			if (s.getPrayer() != null)
			{
				int[] pr = s.getPrayer();
				prayerOrb.setText(String.valueOf(pr[0]));
				prayerOrb.setForeground(UiUtil.levelColor(pr[0], pr[1]));
				prayerOrb.setToolTipText("Prayer " + pr[0] + "/" + pr[1]);
			}
			specOrb.setText(s.getSpec() == null ? "-" : String.valueOf(s.getSpec()));
			specOrb.setForeground(s.getSpec() == null ? UiUtil.RS_YELLOW : UiUtil.levelColor(s.getSpec(), 100));
			runOrb.setText(s.getRun() == null ? "-" : String.valueOf(s.getRun()));
			runOrb.setForeground(s.getRun() == null ? UiUtil.RS_YELLOW : UiUtil.levelColor(s.getRun(), 100));
			runOrb.setToolTipText("Run energy" + (Boolean.TRUE.equals(s.getStamina()) ? " (stamina active)" : ""));
		}

		if (expanded)
		{
			updateDetails();
		}
	}

	private void setExpanded(boolean e)
	{
		expanded = e;
		details.setVisible(e);
		chevron.setText(e ? "▾" : "▸");
		if (e)
		{
			detailsShown = false;
			updateDetails();
		}
		revalidate();
		repaint();
	}

	private void selectTab(Tab tab)
	{
		currentTab = tab;
		pageHolder.removeAll();
		JComponent page;
		switch (tab)
		{
			case EQUIPMENT:
				page = gearPage;
				break;
			case STATUS:
				page = statusPage;
				break;
			case SKILLS:
				page = skillsPage;
				break;
			default:
				page = inventoryPage;
		}
		pageHolder.add(page, BorderLayout.CENTER);
		for (TabButton b : tabButtons)
		{
			b.repaint();
		}
		revalidate();
		repaint();
	}

	private void updateDetails()
	{
		MemberState s = state;
		boolean force = !detailsShown;
		detailsShown = true;

		int[] inv = s == null ? null : s.getInventory();
		if (force || !Arrays.equals(inv, shownInventory))
		{
			shownInventory = inv;
			inventoryPage.setItems(inv);
		}
		int[] eq = s == null ? null : s.getEquipment();
		int[] quiver = s == null ? null : s.getQuiver();
		if (force || !Arrays.equals(eq, shownEquipment) || !Arrays.equals(quiver, shownQuiver))
		{
			shownEquipment = eq;
			shownQuiver = quiver;
			gearPage.setGear(eq, quiver);
		}
		// Cheap to refresh: it only rebuilds prayer icons / rune pouch when those change.
		statusPage.setState(s);
		Map<String, long[]> skills = s == null ? null : s.getSkills();
		if (force || !skillsEqual(skills, shownSkills))
		{
			shownSkills = skills;
			skillsPage.setSkills(skills, s == null ? null : s.getTotalLevel());
		}
	}

	/** Square icon tab, dark with an orange underline when selected (like the in-game side tabs). */
	private final class TabButton extends JComponent
	{
		private final Tab tab;

		TabButton(Tab tab)
		{
			this.tab = tab;
			setPreferredSize(new Dimension(0, 34));
			setToolTipText(tab.tooltip);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					selectTab(tab);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			boolean selected = currentTab == tab;
			g.setColor(selected ? new Color(30, 30, 30) : new Color(37, 37, 37));
			g.fillRect(0, 0, getWidth(), getHeight());
			java.awt.image.BufferedImage icon = services.getSprites().peek(tab.sprite, this::repaint);
			if (icon != null)
			{
				g.drawImage(icon, (getWidth() - icon.getWidth()) / 2, (getHeight() - icon.getHeight()) / 2, null);
			}
			if (selected)
			{
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillRect(4, getHeight() - 2, getWidth() - 8, 2);
			}
		}
	}

	private static boolean skillsEqual(Map<String, long[]> a, Map<String, long[]> b)
	{
		if (a == null || b == null)
		{
			return a == b;
		}
		if (a.size() != b.size())
		{
			return false;
		}
		for (Map.Entry<String, long[]> e : a.entrySet())
		{
			if (!Arrays.equals(e.getValue(), b.get(e.getKey())))
			{
				return false;
			}
		}
		return true;
	}

	private void showMenu(MouseEvent e)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem copyName = new JMenuItem("Copy name");
		copyName.addActionListener(a -> copy(member.getRsn()));
		menu.add(copyName);
		if (state != null && state.getWorld() != null)
		{
			JMenuItem copyWorld = new JMenuItem("Copy world (" + state.getWorld() + ")");
			copyWorld.addActionListener(a -> copy(String.valueOf(state.getWorld())));
			menu.add(copyWorld);
		}

		if (!isSelf)
		{
			boolean hidden = services.getPingSettings().isMuted(member.getRsn());
			JMenuItem mute = new JMenuItem(hidden ? "Show pings from this user" : "Hide pings from this user");
			mute.addActionListener(a -> actions.setPingsHidden(member, !hidden));
			menu.add(mute);
		}

		boolean canEdit = Permissions.canEditPermissions(group, member, selfUserId);
		boolean canKick = Permissions.canKick(group, member, selfUserId);
		boolean canTransfer = !isSelf && group.isOwner();
		if (canEdit || canKick || canTransfer)
		{
			menu.addSeparator();
		}
		if (canEdit)
		{
			JMenuItem perms = new JMenuItem("Permissions...");
			perms.addActionListener(a -> actions.editPermissions(group, member));
			menu.add(perms);
		}
		if (canKick)
		{
			JMenuItem kick = new JMenuItem("Remove from group");
			kick.addActionListener(a -> actions.kick(group, member));
			menu.add(kick);
		}
		if (canTransfer)
		{
			JMenuItem transfer = new JMenuItem("Transfer ownership");
			transfer.addActionListener(a -> actions.transfer(group, member));
			menu.add(transfer);
		}
		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	private static String describe(Set<Permission> perms)
	{
		StringBuilder sb = new StringBuilder();
		for (Permission p : perms)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(p.getDisplayName());
		}
		return sb.toString();
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static void copy(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}
}
