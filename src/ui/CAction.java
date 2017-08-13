package ui;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;


public class CAction extends AbstractAction {
	private static final long serialVersionUID = 1L;

	private Object parentObject_;
	private ActionListener actionListener_;
	private Method actionEventMethod_ = null;
	private boolean isToggleAction_ = false;
	private boolean hasIcon_ = false;
	private boolean isAccelerator_ = true;
	private String caption_;
	private ImageIcon[] imageIcons_;
	private int selectedIconIndex_ = 0;

	public CAction(Object parentObject, String commandKey, String caption, String[] iconNames) {
		super(caption);
		caption_ = caption;

		parentObject_ = parentObject;

		putValue(ACTION_COMMAND_KEY, commandKey);
		putValue(SHORT_DESCRIPTION, caption);
		if (iconNames == null) {
			URL url = getClass().getResource(commandKey + ".png");
			if (url != null) {
				Image imgIcon = Toolkit.getDefaultToolkit().getImage(url);
				putValue(SMALL_ICON,  new ImageIcon(imgIcon));
				hasIcon_ = true;
			}
		} else {
			imageIcons_ = new ImageIcon[iconNames.length];
			int i = 0;;
			for (String iconName : iconNames) {
				URL url = getClass().getResource(iconName + ".png");
				imageIcons_[i++] = new ImageIcon(Toolkit.getDefaultToolkit().getImage(url));
			}
			putValue(SMALL_ICON,  imageIcons_[0]);
			hasIcon_ = true;
		}

		if (parentObject == null) { return; }
		try {
			actionEventMethod_ = parentObject.getClass().getMethod("act" + commandKey + "Executed", ActionEvent.class);
		} catch(NoSuchMethodException e) {
			if (parentObject instanceof ActionListener) {
				actionListener_ = (ActionListener) parentObject;
			} else {
				System.err.println("Action 'act" + commandKey + "Executed' does not exist.");
			}
		}
	}

	public CAction(Object parentObject, String commandKey, String caption) {
		this(parentObject, commandKey, caption, null);
	}

	public CAction(ActionListener actionListener, String commandKey, String caption, boolean selected) {
		this(actionListener, commandKey, caption);

		putValue(SELECTED_KEY, selected);
		isToggleAction_ = true;
	}

	public CAction(Object parentObject, String commandKey, String caption, int shortcutKey, int shortcutKeyMask) {
		this(parentObject, commandKey, caption);

		setKeyStroke(KeyStroke.getKeyStroke(shortcutKey, shortcutKeyMask));
	}

	public CAction(Object parentObject, String commandKey, String caption, int shortcutKey) {
		this(parentObject, commandKey, caption, shortcutKey, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
	}

	public static CAction createKeyBindAction(Object parentObject, String commandKey, String caption) {
		CAction action = new CAction(parentObject, commandKey, caption);
		action.isAccelerator_ = false;
		return action;
	}

	public static CAction createKeyBindAction(Object parentObject, String commandKey, String caption, String[] iconNames) {
		CAction action = new CAction(parentObject, commandKey, caption, iconNames);
		action.isAccelerator_ = false;
		return action;
	}

	public static CAction createKeyBindAction(Object parentObject, String commandKey, String caption, int shortcutKey, int shortcutKeyMask) {
		CAction action = new CAction(parentObject, commandKey, caption);
		action.isAccelerator_ = false;
		if (shortcutKey != 0) {
			KeyStroke keyStroke = KeyStroke.getKeyStroke(shortcutKey, shortcutKeyMask);
			action.setKeyStroke(keyStroke);
		}
		return action;
	}

	public static CAction createKeyBindAction(Object parentObject, String commandKey, String caption, int shortcutKey) {
		return createKeyBindAction(parentObject, commandKey, caption, shortcutKey, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (actionEventMethod_ != null) {
			try {
				actionEventMethod_.invoke(parentObject_, e);
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		} else if (actionListener_ != null) {
			actionListener_.actionPerformed(e);
		}
	}

	public String getCommandKey() { return (String)getValue(ACTION_COMMAND_KEY); }

	@Override
	public Object getValue(String key) {
		return super.getValue(key);
	}

	public boolean isThisAction(ActionEvent e) {
		return e.getActionCommand().equals(getValue(ACTION_COMMAND_KEY));
	}

	public boolean isSelected() {
		return (Boolean)getValue(SELECTED_KEY);
	}

	public void setSelected(boolean selected) {
		putValue(SELECTED_KEY, selected);
	}

	public boolean isToggleAction() { return isToggleAction_; }

	public boolean hasIcon() { return hasIcon_; }

	public void setKeyStroke(KeyStroke keyStroke) {
		putValue(ACCELERATOR_KEY, keyStroke);
		if (isAccelerator_) {
			//putValue(ACCELERATOR_KEY, keyStroke);
		} else {
			putValue("KEY_BIND", keyStroke);
		}
		String text = getKeyStrokeText(keyStroke);
		putValue(SHORT_DESCRIPTION, caption_ + " " + text);
		//putValue(NAME , caption_ + " " + text);
	}

	private String getKeyStrokeText(KeyStroke keyStroke) {
		if (keyStroke == null) { return ""; }
		String text = keyStroke.toString();
		text = text.replace("pressed", "");
		text = text.replaceAll("^ +", "");
		text = text.replaceAll(" +", "+");
		text = text.replace("ctrl", "Ctrl");
		text = text.replace("alt", "Alt");
		text = text.replace("shift", "Shift");
		return text;
	}

	public KeyStroke getKeyStroke() {
		if (isAccelerator_) {
			return (KeyStroke)getValue(ACCELERATOR_KEY);
		} else {
			return (KeyStroke)getValue("KEY_BIND");
		}
	}

	public void setIconIndex(int iconIndex) {
		if (iconIndex != selectedIconIndex_) {
			selectedIconIndex_ = iconIndex;
			putValue(SMALL_ICON,  imageIcons_[iconIndex]);
		}
	}

	public int getIconIndex() { return selectedIconIndex_; }
}
