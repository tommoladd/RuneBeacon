package com.runebeacon.ui;

import com.runebeacon.BeaconClient;
import com.runebeacon.GroupStore;
import com.runebeacon.model.Group;
import com.runebeacon.model.Invite;
import com.runebeacon.model.Member;
import com.runebeacon.model.Permission;
import com.runebeacon.model.Permissions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class RuneBeaconPanel extends PluginPanel implements MemberCard.Actions
{
	private final BeaconClient client;
	private final GroupStore store;
	private final PanelServices services;
	private final Consumer<String> onSelectedGroupChanged;
	private final Runnable onRetry;

	private final JLabel statusLabel = new JLabel();
	private final JLabel statusDetail = new JLabel();
	private final JButton retryButton = new JButton("Retry");
	private final JComboBox<Group> groupBox = new JComboBox<>();
	private final JButton manageButton = new JButton("Manage");
	private final JLabel groupInfo = new JLabel();
	private final JPanel memberList = new JPanel();
	private final JPanel invitesBox = new JPanel();
	private final JPanel groupControls = new JPanel();
	private final Map<String, MemberCard> cards = new HashMap<>();

	private String selectedGroupId;
	private boolean updatingCombo;

	public RuneBeaconPanel(BeaconClient client, GroupStore store, PanelServices services, String initialGroupId,
		Consumer<String> onSelectedGroupChanged, Runnable onRetry)
	{
		this.client = client;
		this.store = store;
		this.services = services;
		this.selectedGroupId = initialGroupId;
		this.onSelectedGroupChanged = onSelectedGroupChanged;
		this.onRetry = onRetry;

		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("RuneBeacon");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(left(title));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		top.add(left(statusLabel));
		statusDetail.setFont(FontManager.getRunescapeSmallFont());
		statusDetail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		top.add(left(statusDetail));
		retryButton.addActionListener(e -> onRetry.run());
		top.add(left(retryButton));
		if (client.isDeveloperMode())
		{
			top.add(Box.createVerticalStrut(4));
			top.add(left(devServerRow()));
		}
		top.add(Box.createVerticalStrut(6));

		groupControls.setLayout(new BoxLayout(groupControls, BoxLayout.Y_AXIS));
		groupBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof Group)
				{
					Group g = (Group) value;
					setText(g.getName() + (g.isTemporary() ? " (temp)" : ""));
				}
				else
				{
					setText("No groups yet");
				}
				return this;
			}
		});
		groupBox.addActionListener(e ->
		{
			if (updatingCombo)
			{
				return;
			}
			Group g = (Group) groupBox.getSelectedItem();
			selectGroup(g == null ? null : g.getGroupId());
		});
		groupControls.add(left(groupBox));

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		JButton create = new JButton("New");
		create.addActionListener(e -> createGroupDialog());
		JButton join = new JButton("Join");
		join.addActionListener(e -> joinGroupDialog());
		manageButton.addActionListener(e -> manageGroupDialog());
		buttons.add(create);
		buttons.add(join);
		buttons.add(manageButton);
		groupControls.add(Box.createVerticalStrut(4));
		groupControls.add(left(buttons));

		groupInfo.setFont(FontManager.getRunescapeSmallFont());
		groupInfo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		groupControls.add(Box.createVerticalStrut(4));
		groupControls.add(left(groupInfo));
		invitesBox.setLayout(new BoxLayout(invitesBox, BoxLayout.Y_AXIS));
		invitesBox.setAlignmentX(LEFT_ALIGNMENT);
		invitesBox.setVisible(false);
		top.add(invitesBox);
		top.add(groupControls);

		add(top, BorderLayout.NORTH);

		memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
		add(memberList, BorderLayout.CENTER);

		statusChanged(client.getStatus(), client.getStatusDetail());
		groupsChanged();
	}

	/** Developer mode only: shows the server in use and lets you point at another (e.g. localhost). */
	private JPanel devServerRow()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
			BorderFactory.createEmptyBorder(4, 6, 4, 4)));
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		Runnable refresh = () ->
		{
			String url = client.effectiveServerUrl();
			label.setText("<html>Dev server:<br><font color='#ffffff'>" + escape(url) + "</font></html>");
			label.setToolTipText(url);
		};
		refresh.run();
		JButton change = new JButton("Change");
		change.addActionListener(e ->
		{
			String current = client.effectiveServerUrl();
			Object input = JOptionPane.showInputDialog(this,
				"<html>Server URL for this dev client, e.g. <b>http://localhost:3000</b><br>"
					+ "Leave blank to use the public server (" + escape(com.runebeacon.RuneBeaconConfig.DEFAULT_SERVER_URL) + ").</html>",
				"Dev server", JOptionPane.PLAIN_MESSAGE, null, null, current);
			if (input != null)
			{
				String v = input.toString().trim();
				client.setDevServerOverride(v.equals(com.runebeacon.RuneBeaconConfig.DEFAULT_SERVER_URL) ? "" : v);
				refresh.run();
			}
		});
		row.add(label, BorderLayout.CENTER);
		row.add(change, BorderLayout.EAST);
		return row;
	}

	private static JPanel left(Component c)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(c, BorderLayout.CENTER);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		return wrap;
	}

	public String getSelectedGroupId()
	{
		return selectedGroupId;
	}

	// ------------------------------------------------------------------ updates (EDT)

	public void statusChanged(BeaconClient.Status status, String detail)
	{
		statusLabel.setText("● " + status.getText());
		statusLabel.setForeground(status == BeaconClient.Status.CONNECTED ? UiUtil.ONLINE
			: status == BeaconClient.Status.ERROR || status == BeaconClient.Status.REPLACED ? UiUtil.HP
			: UiUtil.IDLE);
		statusDetail.setText(detail == null ? "" : "<html>" + escape(detail) + "</html>");
		statusDetail.setVisible(detail != null);
		retryButton.setVisible(status == BeaconClient.Status.ERROR || status == BeaconClient.Status.REPLACED);
		groupControls.setVisible(status == BeaconClient.Status.CONNECTED || !store.getGroups().isEmpty());
		revalidate();
		repaint();
	}

	/** Group list or membership changed: rebuild combo and cards. */
	public void groupsChanged()
	{
		List<Group> groups = store.groupsList();
		updatingCombo = true;
		groupBox.removeAllItems();
		Group selected = null;
		for (Group g : groups)
		{
			groupBox.addItem(g);
			if (g.getGroupId().equals(selectedGroupId))
			{
				selected = g;
			}
		}
		if (selected == null && !groups.isEmpty())
		{
			selected = groups.get(0);
		}
		groupBox.setSelectedItem(selected);
		updatingCombo = false;
		String newId = selected == null ? null : selected.getGroupId();
		if (newId == null ? selectedGroupId != null : !newId.equals(selectedGroupId))
		{
			selectedGroupId = newId;
			onSelectedGroupChanged.accept(newId);
		}
		manageButton.setEnabled(selected != null);
		rebuildInvites();
		rebuildCards();
	}

	/** Refresh values in place for all cards (called on a timer while dirty). */
	public void refreshMembers()
	{
		Group g = store.getGroup(selectedGroupId);
		if (g == null)
		{
			return;
		}
		boolean orderChanged = false;
		List<Member> sorted = sortedMembers(g);
		Component[] comps = memberList.getComponents();
		int ci = 0;
		for (Member m : sorted)
		{
			MemberCard card = cards.get(m.getUserId());
			if (card == null)
			{
				rebuildCards();
				return;
			}
			card.setMember(g, m);
			card.update(store.getState(m.getUserId()));
			// cards are separated by struts, so step 2
			if (ci >= comps.length || comps[ci] != card)
			{
				orderChanged = true;
			}
			ci += 2;
		}
		updateGroupInfo(g);
		if (orderChanged)
		{
			rebuildCards();
		}
	}

	private void selectGroup(String groupId)
	{
		if (groupId == null ? selectedGroupId == null : groupId.equals(selectedGroupId))
		{
			return;
		}
		selectedGroupId = groupId;
		onSelectedGroupChanged.accept(groupId);
		rebuildCards();
	}

	private void rebuildCards()
	{
		memberList.removeAll();
		Group g = store.getGroup(selectedGroupId);
		Map<String, MemberCard> old = new HashMap<>(cards);
		cards.clear();
		if (g == null)
		{
			groupInfo.setText(store.getGroups().isEmpty() ? "<html>Create a group or join one with an invite code.</html>" : "");
		}
		else
		{
			for (Member m : sortedMembers(g))
			{
				MemberCard card = old.get(m.getUserId());
				if (card == null)
				{
					card = new MemberCard(services, this, g, m, store.getSelfUserId());
				}
				card.setMember(g, m);
				card.update(store.getState(m.getUserId()));
				card.setAlignmentX(LEFT_ALIGNMENT);
				cards.put(m.getUserId(), card);
				memberList.add(card);
				memberList.add(Box.createVerticalStrut(4));
			}
			updateGroupInfo(g);
		}
		memberList.revalidate();
		memberList.repaint();
	}

	private List<Member> sortedMembers(Group g)
	{
		List<Member> list = new ArrayList<>(g.getMembers());
		String self = store.getSelfUserId();
		list.sort(Comparator
			.comparing((Member m) -> !m.getUserId().equals(self))
			.thenComparing(m -> !UiUtil.isLoggedIn(m, store.getState(m.getUserId())))
			.thenComparing(m -> !m.isOnline())
			.thenComparing(m -> m.getRsn() == null ? "" : m.getRsn().toLowerCase()));
		return list;
	}

	private void updateGroupInfo(Group g)
	{
		long online = g.getMembers().stream().filter(m -> UiUtil.isLoggedIn(m, store.getState(m.getUserId()))).count();
		String text = online + "/" + g.getMembers().size() + " online";
		if (g.isTemporary() && g.getExpiresAt() != null)
		{
			text += " • " + UiUtil.timeLeft(g.getExpiresAt());
		}
		groupInfo.setText(text);
	}

	// ------------------------------------------------------------------ dialogs

	private void createGroupDialog()
	{
		JTextField name = new JTextField();
		JRadioButton permanent = new JRadioButton("Permanent (friends)", true);
		JRadioButton temporary = new JRadioButton("Temporary (e.g. a raid)");
		ButtonGroup type = new ButtonGroup();
		type.add(permanent);
		type.add(temporary);
		JSpinner hours = new JSpinner(new SpinnerNumberModel(6, 1, 48, 1));
		hours.setEnabled(false);
		temporary.addItemListener(e -> hours.setEnabled(temporary.isSelected()));

		JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
		form.add(new JLabel("Group name:"));
		form.add(name);
		form.add(permanent);
		form.add(temporary);
		form.add(new JLabel("Expires after (hours):"));
		form.add(hours);

		int res = JOptionPane.showConfirmDialog(this, form, "Create group", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (res != JOptionPane.OK_OPTION || name.getText().trim().isEmpty())
		{
			return;
		}
		client.createGroup(name.getText().trim(), temporary.isSelected(), (Integer) hours.getValue(),
			g ->
			{
				selectGroup(g.getGroupId());
				groupsChanged();
				showInvite(g.getName(), g.getInviteCode());
			},
			this::showError);
	}

	private void joinGroupDialog()
	{
		JTextField field = new JTextField(clipboardInviteCode());
		field.setFont(CODE_FONT);
		JPanel form = new JPanel(new GridLayout(0, 1, 2, 4));
		form.add(new JLabel("Invite code:"));
		form.add(field);
		form.add(new JLabel("<html><font color='#a5a5a5'>Dashes, spaces and capitals don't matter.</font></html>"));
		// Focus the field once the dialog is showing.
		field.addAncestorListener(new javax.swing.event.AncestorListener()
		{
			@Override
			public void ancestorAdded(javax.swing.event.AncestorEvent e)
			{
				SwingUtilities.invokeLater(field::requestFocusInWindow);
			}

			@Override
			public void ancestorRemoved(javax.swing.event.AncestorEvent e)
			{
			}

			@Override
			public void ancestorMoved(javax.swing.event.AncestorEvent e)
			{
			}
		});
		int res = JOptionPane.showConfirmDialog(this, form, "Join group", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		String code = field.getText();
		if (res != JOptionPane.OK_OPTION || code == null || code.trim().isEmpty())
		{
			return;
		}
		client.joinGroup(code.replaceAll("[\\s-]", ""), g ->
		{
			selectGroup(g.getGroupId());
			groupsChanged();
		}, this::showError);
	}

	private void manageGroupDialog()
	{
		Group g = store.getGroup(selectedGroupId);
		if (g == null)
		{
			return;
		}
		List<String> options = new ArrayList<>();
		if (g.has(Permission.invite))
		{
			options.add("Invite by name");
			options.add("Invite code");
			options.add("New invite code");
		}
		if (g.has(Permission.rename))
		{
			options.add("Rename");
		}
		if (g.isTemporary() && g.has(Permission.extend))
		{
			options.add("Extend time");
		}
		boolean owner = g.isOwner();
		if (owner)
		{
			options.add("Default permissions");
		}
		options.add("Leave group");
		if (owner)
		{
			options.add("Delete group");
		}

		Set<Permission> mine = g.myPermissionSet();
		String header = "<html><b>" + escape(g.getName()) + "</b><br>"
			+ (g.isTemporary() ? "Temporary group" + (g.getExpiresAt() != null ? ", " + UiUtil.timeLeft(g.getExpiresAt()) : "") : "Permanent group")
			+ "<br>" + (owner ? "You are the owner" : mine.isEmpty() ? "You have no extra permissions" : "Your permissions: " + permissionNames(mine))
			+ "</html>";

		String[] chosen = {null};
		JPanel menu = new JPanel(new GridLayout(0, 1, 0, 4));
		menu.add(new JLabel(header));
		for (String option : options)
		{
			JButton b = new JButton(option);
			b.addActionListener(e ->
			{
				chosen[0] = option;
				SwingUtilities.getWindowAncestor(b).dispose();
			});
			menu.add(b);
		}
		JOptionPane.showOptionDialog(this, menu, "Manage group", JOptionPane.DEFAULT_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Close"}, "Close");
		if (chosen[0] == null)
		{
			return;
		}
		switch (chosen[0])
		{
			case "Extend time":
			{
				Object hours = JOptionPane.showInputDialog(this, "Extend by how many hours?", "Extend group",
					JOptionPane.PLAIN_MESSAGE, null, new Object[]{1, 2, 3, 6, 12, 24, 48}, 2);
				if (hours != null)
				{
					client.extendGroup(g.getGroupId(), (Integer) hours, this::showError);
				}
				break;
			}
			case "Default permissions":
			{
				Set<Permission> chosenSet = PermissionPicker.show(this, "Default permissions",
					"Permissions new members get when they join <b>" + escape(g.getName()) + "</b>:",
					Permissions.of(g.getDefaultPermissions()), Permissions.grantable(g));
				if (chosenSet != null)
				{
					client.setDefaultPermissions(g.getGroupId(), chosenSet, this::showError);
				}
				break;
			}
			case "Invite by name":
				inviteByNameDialog(g, null);
				break;
			case "Invite code":
				showInvite(g.getName(), g.getInviteCode());
				break;
			case "New invite code":
				if (confirm("Create a new invite code? The old one will stop working."))
				{
					client.regenerateInvite(g.getGroupId(), code -> showInvite(g.getName(), code), this::showError);
				}
				break;
			case "Rename":
			{
				String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename group",
					JOptionPane.PLAIN_MESSAGE, null, null, g.getName());
				if (name != null && !name.trim().isEmpty())
				{
					client.renameGroup(g.getGroupId(), name.trim(), this::showError);
				}
				break;
			}
			case "Leave group":
				if (confirm("Leave " + g.getName() + "?" + (owner ? " Ownership will pass to another member." : "")))
				{
					client.leaveGroup(g.getGroupId(), this::showError);
				}
				break;
			case "Delete group":
				if (confirm("Delete " + g.getName() + " for everyone? This can't be undone."))
				{
					client.deleteGroup(g.getGroupId(), this::showError);
				}
				break;
			default:
				break;
		}
	}

	private void showInvite(String groupName, String code)
	{
		if (code == null)
		{
			showError("You need the Invite permission to see the invite code.");
			return;
		}
		String pretty = formatCode(code);
		JTextField codeField = new JTextField(pretty);
		codeField.setEditable(false);
		codeField.setFont(CODE_FONT.deriveFont(24f));
		codeField.setHorizontalAlignment(JTextField.CENTER);
		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.add(new JLabel("<html>Invite code for <b>" + escape(groupName) + "</b>:</html>"), BorderLayout.NORTH);
		body.add(codeField, BorderLayout.CENTER);
		body.add(new JLabel("<html>Friends paste it into <b>Join</b>.<br>"
			+ "<font color='#a5a5a5'>Tip: <b>Invite by name</b> skips the code,<br>or right-click a friend and choose "
			+ "<b>RuneBeacon invite</b>.</font></html>"), BorderLayout.SOUTH);
		Object[] options = {"Copy", "Close"};
		int res = JOptionPane.showOptionDialog(this, body, "Invite friends", JOptionPane.DEFAULT_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if (res == 0)
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pretty), null);
		}
	}

	// ------------------------------------------------------------------ invites

	private static final java.awt.Font CODE_FONT = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 16);
	private static final java.util.regex.Pattern CODE_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z2-9]{4}[\\s-]?[A-Za-z2-9]{4}$");

	/** "JFP3ZAXZ" -> "JFP3-ZAXZ", easier to read out and type. */
	static String formatCode(String code)
	{
		String c = code.replaceAll("[\\s-]", "").toUpperCase();
		return c.length() == 8 ? c.substring(0, 4) + "-" + c.substring(4) : c;
	}

	/** If the clipboard holds something that looks like an invite code, pre-fill it. */
	private static String clipboardInviteCode()
	{
		try
		{
			Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
			if (data instanceof String)
			{
				String s = ((String) data).trim();
				if (CODE_PATTERN.matcher(s).matches())
				{
					return formatCode(s);
				}
			}
		}
		catch (Exception ignored)
		{
			// Clipboard unavailable or not text.
		}
		return "";
	}

	/** Entry point for the friends list / chat right-click "RuneBeacon invite" option. */
	public void inviteByName(String rsn)
	{
		List<Group> eligible = store.groupsWithInvitePermission();
		if (eligible.isEmpty())
		{
			showError("You need a group where you have the Invite permission first.");
			return;
		}
		Group preferred = store.getGroup(selectedGroupId);
		inviteByNameDialog(preferred != null && eligible.contains(preferred) ? preferred : eligible.get(0), rsn);
	}

	private void inviteByNameDialog(Group initial, String rsn)
	{
		List<Group> eligible = store.groupsWithInvitePermission();
		JTextField name = new JTextField(rsn == null ? "" : rsn);
		JComboBox<String> groupPick = new JComboBox<>();
		for (Group g : eligible)
		{
			groupPick.addItem(g.getName() + (g.isTemporary() ? " (temp)" : ""));
		}
		groupPick.setSelectedIndex(Math.max(0, eligible.indexOf(initial)));

		JPanel form = new JPanel(new GridLayout(0, 1, 2, 4));
		form.add(new JLabel("Player name:"));
		form.add(name);
		form.add(new JLabel("Group:"));
		form.add(groupPick);
		int res = JOptionPane.showConfirmDialog(this, form, "Invite by name", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		String target = name.getText().trim();
		if (res != JOptionPane.OK_OPTION || target.isEmpty() || groupPick.getSelectedIndex() < 0)
		{
			return;
		}
		Group g = eligible.get(groupPick.getSelectedIndex());
		client.inviteByName(g.getGroupId(), target,
			() -> JOptionPane.showMessageDialog(this,
				"<html>Invite sent.<br>If <b>" + escape(target) + "</b> uses RuneBeacon, they'll see it in their panel.</html>",
				"RuneBeacon", JOptionPane.INFORMATION_MESSAGE),
			this::showError);
	}

	private void rebuildInvites()
	{
		invitesBox.removeAll();
		List<Invite> invites = store.getInvites();
		invitesBox.setVisible(!invites.isEmpty());
		for (Invite inv : invites)
		{
			JPanel card = new JPanel(new BorderLayout(0, 4));
			card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
				BorderFactory.createEmptyBorder(6, 6, 6, 6)));
			JLabel text = new JLabel("<html><b>" + escape(inv.getInvitedBy()) + "</b> invited you to <b>"
				+ escape(inv.getGroupName()) + "</b>" + ("temporary".equals(inv.getGroupType()) ? " (temp)" : "")
				+ "<br><font color='#a5a5a5'>" + inv.getMemberCount() + " member" + (inv.getMemberCount() == 1 ? "" : "s")
				+ "</font></html>");
			text.setFont(FontManager.getRunescapeSmallFont());
			text.setForeground(Color.WHITE);
			card.add(text, BorderLayout.CENTER);
			JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
			buttons.setOpaque(false);
			JButton accept = new JButton("Accept");
			accept.addActionListener(e -> client.acceptInvite(inv.getInviteId(), g ->
			{
				selectGroup(g.getGroupId());
				groupsChanged();
			}, this::showError));
			JButton decline = new JButton("Decline");
			decline.addActionListener(e -> client.declineInvite(inv.getInviteId(), this::showError));
			buttons.add(accept);
			buttons.add(decline);
			card.add(buttons, BorderLayout.SOUTH);
			card.setAlignmentX(LEFT_ALIGNMENT);
			invitesBox.add(card);
			invitesBox.add(Box.createVerticalStrut(4));
		}
		invitesBox.revalidate();
		invitesBox.repaint();
	}

	@Override
	public void kick(Group group, Member member)
	{
		if (confirm("Remove " + member.getRsn() + " from " + group.getName() + "?"))
		{
			client.kick(group.getGroupId(), member.getUserId(), this::showError);
		}
	}

	@Override
	public void editPermissions(Group group, Member member)
	{
		String heading = "Permissions for <b>" + escape(member.getRsn()) + "</b>"
			+ (group.isOwner() ? "" : "<br>You can only grant permissions you have,<br>and not all of them.");
		Set<Permission> chosen = PermissionPicker.show(this, "Member permissions", heading,
			Permissions.of(member.getPermissions()), Permissions.grantable(group));
		if (chosen == null)
		{
			return;
		}
		if (!Permissions.canGrant(group, chosen))
		{
			showError("You can't give someone every permission you have. Untick at least one.");
			return;
		}
		client.setPermissions(group.getGroupId(), member.getUserId(), chosen, this::showError);
	}

	private static String permissionNames(Set<Permission> perms)
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

	@Override
	public void transfer(Group group, Member member)
	{
		if (confirm("Make " + member.getRsn() + " the owner of " + group.getName() + "? You'll keep every permission except deleting the group."))
		{
			client.transferOwnership(group.getGroupId(), member.getUserId(), this::showError);
		}
	}

	@Override
	public void pingItem(Group group, Member member, int slot, int itemId, String itemName)
	{
		if (!client.sendItemPing(group.getGroupId(), member.getUserId(), slot, itemId))
		{
			showError("Not connected to the RuneBeacon server");
			return;
		}
		String self = store.getSelfUserId();
		flashItem(member.getUserId(), slot, self == null ? Color.WHITE : UiUtil.memberColor(self),
			services.getPingSettings().durationMs());
	}

	@Override
	public void setPingsHidden(Member member, boolean hidden)
	{
		services.getPingSettings().setMuted(member.getRsn(), hidden);
	}

	/** Flash an inventory slot on a member's card (item ping). */
	public void flashItem(String userId, int slot, Color color, int durationMs)
	{
		MemberCard card = cards.get(userId);
		if (card != null)
		{
			card.flashInventorySlot(slot, color, durationMs);
		}
	}

	private boolean confirm(String message)
	{
		return JOptionPane.showConfirmDialog(this, message, "RuneBeacon", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}

	private void showError(String message)
	{
		JOptionPane.showMessageDialog(this, message, "RuneBeacon", JOptionPane.WARNING_MESSAGE);
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
