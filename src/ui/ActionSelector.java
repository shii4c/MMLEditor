package ui;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.Action;
import javax.swing.JButton;

public class ActionSelector implements Action, PropertyChangeListener {
	private Action action_;
	private ArrayList<PropertyChangeListener> listeners_ = new ArrayList<PropertyChangeListener>();
	private HashSet<String> propKeySet_ = new HashSet<String>();

	public ActionSelector(Action action) {
		selectAction(action);
	}

	public void selectAction(Action action) {
		if (action_ != null) {
			action_.removePropertyChangeListener(this);
		}
		Action oldAction = action_;
		action_ = action;
		if (action != null) {
			action.addPropertyChangeListener(this);
			for (String key : propKeySet_) {
				Object oldValue = null;
				if (oldAction != null) { oldValue = oldAction.getValue(key); }
				propertyChange(new PropertyChangeEvent(action, key, oldValue, action.getValue(key)));
			}
		}
	}

	public JButton createToolbarButton() {
		JButton button = new JButton(this);
		button.setFocusable(false);
		button.setHideActionText(true);
		return button;
	}

	// -------------------------------------------------------------------------------------
	// Action �C���^�[�t�F�[�X�̃C���v�������g
	// -------------------------------------------------------------------------------------
	@Override
	public void actionPerformed(ActionEvent e) {
		action_.actionPerformed(e);
	}

	@Override
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		listeners_.add(listener);
	}

	@Override
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		listeners_.remove(listener);
	}

	@Override
	public Object getValue(String key) {
		if (action_ == null) { return null; }
		propKeySet_.add(key);
		return action_.getValue(key);
	}

	@Override
	public boolean isEnabled() {
		if (action_ == null) { return false; }
		return action_.isEnabled();
	}

	@Override
	public void putValue(String key, Object value) {
		if (action_ != null) {
			action_.putValue(key, value);
		}
	}

	@Override
	public void setEnabled(boolean b) {
		if (action_ != null) {
			action_.setEnabled(b);
		}
	}

	// -------------------------------------------------------------------------------------
	// PropertyChangeListener
	// -------------------------------------------------------------------------------------
	@Override
	public void propertyChange(PropertyChangeEvent e) {
		for (Object listener : listeners_.toArray()) {
			((PropertyChangeListener)listener).propertyChange(e);
		}
	}
}
