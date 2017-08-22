package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import audio.MMLParser;
import audio.MMLParser.TimeRange;
import audio.MMLPlayer2;

@SuppressWarnings("serial")
public class MMLEditFrame extends JFrame implements WindowListener {
	private GridLayout centerGridLayout_ = new GridLayout(3, 1);
	private JPanel centerPanel_ = new JPanel(centerGridLayout_);
	private JToolBar toolbar_ = new JToolBar();
	private ArrayList<JTextArea> mmlEditors_ = new ArrayList<>();
	private CAction actPlay_   = new CAction(this, "Play", "Play");
	private CAction actStop_   = new CAction(this, "Stop", "Stop");
	private ActionSelector actPlayStop_ = new ActionSelector(actPlay_);
	private CAction actLoad_   = new CAction(this, "Load", "Load");
	private CAction actSave_   = new CAction(this, "Save", "Save");
	private CAction actSaveAs_ = new CAction(this, "SaveAs", "Save as");
	private JFileChooser fileChooser_;
	private File currentFile_;
	private boolean isModified_;

	private MMLPlayer2 player_ = new MMLPlayer2();
	private Thread playThread_;
	private ArrayList<MMLInfo> mmlInfoList_ = new ArrayList<>();
	private Timer timer_;
	private int refreshCounter_ = 1;

	public MMLEditFrame() {
		setLayout(new BorderLayout());
		setSize(720, 480);

		// North
		add(toolbar_, BorderLayout.NORTH);
		toolbar_.add(actPlayStop_.createToolbarButton());

		// Center
		add(centerPanel_, BorderLayout.CENTER);
		for (int i = 0; i < 3; i++) {
			addEditor();
		}

		// Menu
		JMenuBar menuBar = new JMenuBar();
		JMenu mnuFile = new JMenu("File");
		mnuFile.add(new JMenuItem(actLoad_));
		mnuFile.add(new JMenuItem(actSave_));
		mnuFile.add(new JMenuItem(actSaveAs_));
		menuBar.add(mnuFile);
		JMenu mnuPlay = new JMenu("Play");
		mnuPlay.add(new JMenuItem(actPlayStop_));
		menuBar.add(mnuPlay);
		setJMenuBar(menuBar);

		// Timer
		timer_ = new Timer(100, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try { timer(); } catch(Exception ex) { ex.printStackTrace(); }
			}
		});
		timer_.start();

		actPlay_.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		actStop_.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		actSave_.setKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_MASK));

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(this);
		setTitle();
	}

	// ----------------------------------------------------------
	@Override public void windowActivated(WindowEvent e) {}
	@Override public void windowDeactivated(WindowEvent e) {}
	@Override public void windowDeiconified(WindowEvent e) {}
	@Override public void windowIconified(WindowEvent e) {}
	@Override public void windowOpened(WindowEvent e) {}
	@Override public void windowClosed(WindowEvent e) {}
	@Override
	public void windowClosing(WindowEvent e) {
		if (confirmSaveAndContinue()) {
			System.exit(0);
		}
	}
	// ----------------------------------------------------------

	private void setTitle() {
		String title = "無題";
		if (currentFile_ != null) { title = currentFile_.getName() + " (" + currentFile_.getPath() + ")"; }
		if (isModified_) { title += " [変更]"; }
		setTitle(title);
	}

	private void addEditor() {
		JTextArea mmlEditor = new JTextArea();
		mmlEditor.setLineWrap(true);
		mmlEditors_.add(mmlEditor);
		centerPanel_.add(new JScrollPane(mmlEditor));
		mmlInfoList_.add(new MMLInfo(mmlEditor));
	}

	private void removeEditor(int index) {
		MMLInfo mmlInfo = mmlInfoList_.remove(index);
		mmlEditors_.remove(mmlInfo.editor_);
		centerPanel_.remove(index);
	}

	private void timer() {
		if (refreshCounter_ > 0) {
			refreshCounter_--;
			if (refreshCounter_ == 0) {
				if (mmlInfoList_.get(0).isNeedRefresh()) {
					for (MMLInfo mmlInfo : mmlInfoList_) { mmlInfo.setNeedRefresh(); }
				}
				MMLInfo focusedMMLInfo = null;
				for (MMLInfo mmlInfo : mmlInfoList_) {
					mmlInfo.refresh(false);
					if (mmlInfo.editor_.isFocusOwner()) {
						focusedMMLInfo = mmlInfo;
					}
				}
				if (focusedMMLInfo != null) {
					int startPos = focusedMMLInfo.editor_.getSelectionStart();
					int endPos = focusedMMLInfo.editor_.getSelectionEnd();
					if (startPos == endPos) { endPos++; }
					TimeRange timeRange = focusedMMLInfo.mml_.getTimeRange(startPos, endPos);
					if (timeRange == null) { return; }
					for (MMLInfo mmlInfo : mmlInfoList_) {
						mmlInfo.update(timeRange);
					}
				}
			}
		}

		// update playing position
		if (playThread_ != null) {
			MMLParser.Note[] notes = player_.getCurrentPlayingNotes();
			for(MMLInfo mmlInfo : mmlInfoList_) {
				mmlInfo.updatePlaying(notes);
			}
		}
	}

	private void textChanged() {
		if (!isModified_) {
			isModified_ = true;
			setTitle();
		}
	}

	public void actPlayExecuted(ActionEvent e) {
		if (playThread_ != null) {
			player_.requestStop();
			return;
		}

		MMLInfo focusedMMLInfo = null;
		final MMLParser[] mmlList = new MMLParser[mmlInfoList_.size()];
		for (int i = 0; i < mmlInfoList_.size(); i++) {
			MMLInfo mmlInfo = mmlInfoList_.get(i);
			mmlInfo.refreshPositions();
			mmlList[i] = mmlInfo.mmlForPlay_;
			if (mmlInfo.editor_.isFocusOwner()) { focusedMMLInfo = mmlInfo; }
		}
		final TimeRange timeRange = new TimeRange(0, Double.MAX_VALUE);
		if (focusedMMLInfo != null && focusedMMLInfo.editor_.getSelectionEnd() - focusedMMLInfo.editor_.getSelectionStart() > 0) {
			int startPos = focusedMMLInfo.editor_.getSelectionStart();
			int endPos = focusedMMLInfo.editor_.getSelectionEnd();
			TimeRange selectedTimeRange = focusedMMLInfo.mmlForPlay_.getTimeRange(startPos, endPos);
			timeRange.startTime = selectedTimeRange.startTime;
			timeRange.endTime = selectedTimeRange.endTime;
			System.out.println(timeRange);
		}

		actPlayStop_.selectAction(actStop_);
		playThread_ = new Thread(new Runnable() {
			public void run() {
				try {
					player_.play(mmlList, timeRange.startTime, timeRange.endTime);
				} catch(Exception e) {}
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						playThread_ = null;
						for(MMLInfo mmlInfo : mmlInfoList_) {
							mmlInfo.updatePlaying(null);
						}
						actPlayStop_.selectAction(actPlay_);
					}
				});
			}
		});
		playThread_.start();
	}

	public void actStopExecuted(ActionEvent e) {
		if (playThread_ != null) {
			player_.requestStop();
		}
	}

	public void actLoadExecuted(ActionEvent e) {
		if (!confirmSaveAndContinue()) { return; }
		createFileChooser();
		if (fileChooser_.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			load(fileChooser_.getSelectedFile());
		}
	}

	public void actSaveAsExecuted(ActionEvent e) {
		save(true);
	}

	public void actSaveExecuted(ActionEvent e) {
		save(false);
	}

	private boolean save(boolean isSaveAs) {
		if (isSaveAs || currentFile_ == null) {
			createFileChooser();
			if (fileChooser_.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				save(fileChooser_.getSelectedFile());
				return true;
			} else {
				return false;
			}
		}
		save(currentFile_);
		return true;
	}

	private void save(File file) {
		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
			for (MMLInfo mmlInfo : mmlInfoList_) {
				writer.write(mmlInfo.editor_.getText());
				writer.write(";\n");
			}
			writer.close();

			currentFile_ = file;
			isModified_ = false;
			setTitle();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void load(File file) {
		try {
			StringBuilder sb = new StringBuilder();
			char[] buff = new char[4096];
			Reader reader = new InputStreamReader(new FileInputStream(file), "utf-8");
			while(true) {
				int len = reader.read(buff);
				if (len <= 0) { break; }
				sb.append(buff, 0, len);
			}
			reader.close();

			int index = 0;
			for (String mml : sb.toString().split(";")) {
				if (mml.trim().equals("")) { continue; }
				if (index >= mmlInfoList_.size()) {
					addEditor();
				}
				if (mml.startsWith("\r\n")) { mml = mml.substring(2); }
				else if (mml.startsWith("\n")) { mml = mml.substring(1); }
				MMLInfo mmlInfo = mmlInfoList_.get(index);
				mmlInfo.editor_.setText(mml);
				index++;
			}
			for (int i = index; i < 3 && i < mmlInfoList_.size(); i++) {
				mmlInfoList_.get(i).editor_.setText("");
			}
			int n = mmlInfoList_.size();
			for (int i = index; i < n; i++) {
				removeEditor(3);
			}
			centerGridLayout_.setRows(mmlInfoList_.size());
			centerPanel_.doLayout();

			currentFile_ = file;
			isModified_ = false;
			setTitle();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private boolean confirmSaveAndContinue() {
		if (!isModified_) { return true; }
		int result = JOptionPane.showConfirmDialog(this, "現在の変更を保存しますか", "保存の確認", JOptionPane.YES_NO_CANCEL_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			return save(false);
		}
		if (result == JOptionPane.NO_OPTION) {
			return true;
		}
		return false;
	}

	private JFileChooser createFileChooser() {
		if (fileChooser_ == null) {
			fileChooser_ = new JFileChooser();
		}
		return fileChooser_;
	}

	private class MMLInfo implements DocumentListener, CaretListener {
		private MMLParser mml_;
		private MMLParser mmlForPlay_;
		private Object caretTimeHighlightTag_;
		private Object playingHighlightTag_;
		private MMLParser.Note prevHighlightNote_;
		private Position[] positions_;
		private JTextComponent editor_;
		private boolean needRefresh_ = true;

		public MMLInfo(JTextComponent editor) {
			editor_ = editor;
			editor.getDocument().addDocumentListener(this);
			editor.addCaretListener(this);
			Highlighter h = editor_.getHighlighter();
			try {
				playingHighlightTag_ = h.addHighlight(0, 0, new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
				caretTimeHighlightTag_ = h.addHighlight(0, 0, new DefaultHighlighter.DefaultHighlightPainter(Color.LIGHT_GRAY));
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		public boolean isNeedRefresh() { return needRefresh_; }
		public void setNeedRefresh() { needRefresh_ = true; }

		private void refresh(boolean isForPlay) {
			if (needRefresh_ || isForPlay) {
				ArrayList<MMLParser.Tempo> tempoList = null;
				if (mmlInfoList_.get(0) != this) {
					tempoList = mmlInfoList_.get(0).mml_.getTempoList();
				}
				String mmlText = editor_.getText();
				mml_ = new MMLParser(mmlText, tempoList);
				if (isForPlay) { mmlForPlay_ = new MMLParser(mmlText, tempoList); }
				needRefresh_ = false;
			}
		}

		private void refreshPositions() {
			try {
				refresh(true);
				Document doc = editor_.getDocument();
				String mmlText = editor_.getText();
				positions_ = new Position[mmlText.length() + 1];
				ArrayList<MMLParser.Element> elems = mmlForPlay_.getMMLElements();
				for (MMLParser.Element elem : elems) {
					int pos = elem.getStartPos();
					if (positions_[pos] == null) { positions_[pos] = doc.createPosition(pos); }
					pos = elem.getEndPos();
					if (positions_[pos] == null) { positions_[pos] = doc.createPosition(pos); }
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		private void update(TimeRange timeRange) {
			ArrayList<MMLParser.Element> elems = mml_.findElements(timeRange);
			if (elems.size() == 0) {
				hideCaretTimeHightlight();
				return;
			}
			//
			int startPos = elems.get(0).getStartPos();
			int endPos = elems.get(elems.size() - 1).getEndPos();
			Highlighter h = editor_.getHighlighter();
			try {
				h.changeHighlight(caretTimeHighlightTag_, startPos, endPos);
			} catch(Exception e2) {}
		}

		private void hideCaretTimeHightlight() {
			try {
				Highlighter h = editor_.getHighlighter();
				h.changeHighlight(caretTimeHighlightTag_, 0, 0);
			} catch(Exception e) {}
		}

		private void hidePlaying() {
			try {
				Highlighter h = editor_.getHighlighter();
				h.changeHighlight(playingHighlightTag_, 0, 0);
			} catch(Exception e) {}
		}

		private void updatePlaying(MMLParser.Note[] notes) {
			if (notes == null) {
				hidePlaying();
				return;
			}
			MMLParser.Note targetNote = null;
			for (MMLParser.Note note : notes) {
				if (note != null && note.isParent(mmlForPlay_)) {
					targetNote = note;
					break;
				}
			}

			if (targetNote == prevHighlightNote_) {
				return;
			}
			prevHighlightNote_ = targetNote;
			if (targetNote != null) {
				Highlighter h = editor_.getHighlighter();
				try {
					h.changeHighlight(playingHighlightTag_, positions_[targetNote.getStartPos()].getOffset(), positions_[targetNote.getEndPos()].getOffset());
				} catch(Exception e) {}
			} else {
				hidePlaying();
			}
		}

		@Override
		public void changedUpdate(DocumentEvent arg0) {
			needRefresh_ = true;
			refreshCounter_ = 3;
			textChanged();
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			needRefresh_ = true;
			refreshCounter_ = 3;
			textChanged();
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			needRefresh_ = true;
			refreshCounter_ = 3;
			textChanged();
		}

		@Override
		public void caretUpdate(CaretEvent arg0) {
			refreshCounter_ = 3;
		}
	}

}
