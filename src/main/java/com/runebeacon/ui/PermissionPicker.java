package com.runebeacon.ui;

import com.runebeacon.model.Permission;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Checkbox dialog for choosing a set of permissions. */
final class PermissionPicker
{
	private PermissionPicker()
	{
	}

	/**
	 * @param current   initially ticked permissions
	 * @param grantable permissions the viewer may tick/untick; others are shown but disabled
	 * @return the chosen set, or null if cancelled
	 */
	static Set<Permission> show(Component parent, String title, String heading, Set<Permission> current, Set<Permission> grantable)
	{
		JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
		form.add(new JLabel("<html>" + heading + "</html>"));
		Map<Permission, JCheckBox> boxes = new EnumMap<>(Permission.class);
		for (Permission p : Permission.values())
		{
			JCheckBox box = new JCheckBox(p.getDisplayName(), current.contains(p));
			box.setToolTipText(p.getDescription());
			box.setEnabled(grantable.contains(p));
			boxes.put(p, box);
			form.add(box);
		}

		int res = JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (res != JOptionPane.OK_OPTION)
		{
			return null;
		}
		Set<Permission> out = EnumSet.noneOf(Permission.class);
		for (Map.Entry<Permission, JCheckBox> e : boxes.entrySet())
		{
			if (e.getValue().isSelected())
			{
				out.add(e.getKey());
			}
		}
		return out;
	}
}
