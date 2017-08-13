package audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class MMLParser {
	private static HashMap<Character, Double> freqTbl = new HashMap<>();
	private static double[] o2freq;
	static {
		freqTbl.put('C', Math.pow(2, 3.0 / 12));
		freqTbl.put('D', Math.pow(2, 5.0 / 12));
		freqTbl.put('E', Math.pow(2, 7.0 / 12));
		freqTbl.put('F', Math.pow(2, 8.0 / 12));
		freqTbl.put('G', Math.pow(2, 10.0 / 12));
		freqTbl.put('A', Math.pow(2, 12.0 / 12));
		freqTbl.put('B', Math.pow(2, 14.0 / 12));

		o2freq = new double[9];
		double freq = 55 / 2;
		for (int i = 0; i < o2freq.length; i++) {
			o2freq[i] = freq;
			freq *= 2;
		}
	}
	
	private ArrayList<Element> mmlElements_ = new ArrayList<>();
	private ArrayList<Tempo> tempoList_ = new ArrayList<>();
	
	private String mml_;
	private char currentC_;
	private char nextC_;
	private int pos_;
	
	public MMLParser(String mml, ArrayList<Tempo> prevTempoList) {
		mml_ = mml.toUpperCase();
		double defaultT = 0.25;
		int currentO = 4;
		int soundKind = 0;
		double tm = 60.0 / 120 * 4;//   1.0f * 4 / 2;
		float volume = 1;
		Stack<LoopStart> loopStack = new Stack<>();
		boolean isTie = false;
		double firstLoopTime = 0;
		int prevTempoReadingIndex = 0;
		
		while(pos_ < mml_.length()) {
			int startPos = pos_;
			if (prevTempoList != null && prevTempoReadingIndex < prevTempoList.size()) {
				Tempo tempo = prevTempoList.get(prevTempoReadingIndex);
				if (tempo.time - 0.001 <= firstLoopTime) {
					prevTempoReadingIndex++;
					tm = 60.0 / tempo.tempo * 4;
				}
			}
			currentC_ = mml_.charAt(pos_++);
			nextC_ = pos_ < mml_.length() ? mml_.charAt(pos_) : 0;
			//if (currentC == '!') { return; }

			if (isNote(currentC_) || currentC_ == 'R' || currentC_ == 'L' || currentC_ == 'N') {
				double t = defaultT;
				double f = 0;
				if (currentC_ == 'N') {
					int noteNum = readNumber();
					f = o2freq[noteNum / 12] * Math.pow(2, (3 + noteNum % 12) / 12.0);
				} else if (currentC_ != 'R' && currentC_ != 'L') {
					f = o2freq[currentO] * freqTbl.get(currentC_);

					if (nextC_ == '#' || nextC_ == '+') {
						f *= Math.pow(2, 1.0 / 12);
						pos_++;
						nextC_ = pos_ < mml_.length() ? mml_.charAt(pos_) : 0;
					} else if (nextC_ == '-') {
						f *= Math.pow(2, -1.0 / 12);
						pos_++;
						nextC_ = pos_ < mml_.length() ? mml_.charAt(pos_) : 0;
					}
				}
				int n = currentC_ != 'N' ? readNumber() : 0;
				if (n > 0) {
					t = 1.0 / n;
				}
				if (nextC_ == '.') {
					t *= 1.5;
					pos_++;
				}
				if (currentC_ != 'L') {
					double len = tm * t;
					boolean needAdd = true;
					if (isTie && mmlElements_.size() > 0) {
						Element prevElem = mmlElements_.get(mmlElements_.size() - 1);
						if (prevElem instanceof Note && f == ((Note) prevElem).getFreqs()) {
							prevElem.endPos_ = pos_;
							prevElem.time_ += len;
							needAdd = false;
						}
					}
					if (needAdd) {
						mmlElements_.add(new Note(firstLoopTime, startPos, pos_, f, len, soundKind, volume));
					}
					firstLoopTime += len;
					isTie = false;
				} else {
					defaultT = t;
				}
			} else if (currentC_ == '>') {
				currentO++;
			} else if (currentC_ == '<') {
				currentO--;
			} else if (currentC_ == 'O') {
				currentO = readNumber();
			} else if (currentC_ == '{') {
				int loopN = readNumber(2);
				if (loopN == 0) { loopN = 10000; }
				LoopStart loopStart = new LoopStart(firstLoopTime, startPos, pos_, loopN);
				mmlElements_.add(loopStart);
				loopStack.push(loopStart);
				isTie = false;
			} else if (currentC_ == '}') {
				LoopStart loopStart = loopStack.pop();
				mmlElements_.add(new LoopEnd(firstLoopTime, startPos, pos_, loopStart));
				isTie = false;
				if (loopStart.count_ > 0) {
					firstLoopTime += (firstLoopTime - loopStart.getFirstLoopStartTime()) * (loopStart.count_ - 1);
				} else {
					firstLoopTime = Double.MAX_VALUE;
				}
			} else if (currentC_ == 'T') {
				int n = readNumber();
				tm = (float)(60.0 / n * 4);
				tempoList_.add(new Tempo(firstLoopTime, n));
			} else if (currentC_ == '@') {
				soundKind = readNumber();
			} else if (currentC_ == '&') {
				isTie = true;
			} else if (currentC_ == 'V') {
				volume = (float) Math.pow(2, (readNumber() - 8) / 3.0);
			}
		}
		for (int i = 0; i < mmlElements_.size(); i++) {
			if (i < mmlElements_.size() - 1) {
				mmlElements_.get(i).next_ = mmlElements_.get(i + 1);
			}
		}
	}
	
	public ArrayList<Element> getMMLElements() {
		return mmlElements_;
	}
	
	public ArrayList<Tempo> getTempoList() { return tempoList_; }
	
	public ArrayList<Element> findElements(TimeRange timeRange) {
		ArrayList<Element> elems = new ArrayList<>();
		if (mmlElements_ == null || mmlElements_.size() == 0) { return elems; }
		Env env = new Env();
		int counter = 4000;
		Element elem = mmlElements_.get(0);
		while(counter-- > 0 && elem != null) {
			if (timeRange.endTime - 0.0001 <= env.getCurrentTime()) { break; }
			if (elem.time_ > 0) {
				if (timeRange.startTime + 0.0001 <= env.currentTime_ + elem.time_) {
					elems.add(elem);
				}
			}
			elem = elem.toNext(env);
		}
		return elems;
	}
	
	public TimeRange getTimeRange(int pos, int endPos) {
		if (mmlElements_ == null || mmlElements_.size() == 0) { return null; }
		double startTime = 0;
		double endTime = 0;
		boolean isStart = false;
		int counter = 4000;
		Env env = new Env();
		Element elem = mmlElements_.get(0);
		while(counter-- > 0 && elem != null) {
			if (elem.startPos_ >= endPos) { break; }
			if (elem.time_ > 0) {
				if (elem.endPos_ > pos ) {
					if (!isStart) {
						startTime = env.currentTime_;
						isStart = true;
					}
					endTime = env.currentTime_ + elem.time_;
				}
			}
			elem = elem.toNext(env);
		}
		if (isStart) {
			return new TimeRange(startTime, endTime);
		}
		return null;
	}
	
	public Element findElementFromPos(int pos) {
		for (Element elem : mmlElements_) {
			if (elem.startPos_ >= pos && pos < elem.endPos_) { return elem; }
			//if (elem.endPos_ < pos) { break; }
		}
		return null;
	}
	
	private int readNumber() { return readNumber(0); }
	
	private int readNumber(int defVal) {
		int len = 0;
		int n = 0;
		while(isNumber(nextC_)) {
			n = n * 10 + (nextC_ - '0');
			pos_++;
			nextC_ = pos_ < mml_.length() ? mml_.charAt(pos_) : 0;
			len++;
		}
		if (len == 0) { return defVal; }
		return n;
	}
	
	private boolean isNumber(char c) { return c >= '0' && c <= '9'; }
	private boolean isNote(char c) { return c >= 'A' && c <= 'G'; }
	
	
	public static class TimeRange {
		public double startTime;
		public double endTime;
		
		public TimeRange(double startTime, double endTime) {
			this.startTime = startTime;
			this.endTime = endTime;
		}
		
		public String toString() { return "TimeRange(" + startTime + ":" + endTime + ")"; }
	}
	
	public static class CharRange {
		public int startPos;
		public int endPos;
		
		public CharRange(int startPos, int endPos) {
			this.startPos = startPos;
			this.endPos = endPos;
		}
	}
	
	public static class Env {
		private int[] stack_ = new int[64];
		private int sp_;
		private double currentTime_ = 0;
		
		public int pop() { return stack_[--sp_]; }
		public void push(int val) { stack_[sp_++] = val; }
		public double getCurrentTime() { return currentTime_; }
	}
	
	public static class Tempo {
		public double time;
		public int tempo;
		public Tempo(double time, int tempo) {
			this.time = time;
			this.tempo = tempo;
		}
	}
	
	public class Element {
		protected Element next_;
		private double time_;
		private double firstLoopStartTime_;
		private int startPos_, endPos_;
		
		public Element(double firstLoopStartTime, int startPos, int endPos, double time) {
			startPos_ = startPos;
			endPos_ = endPos;
			time_ = time;
			firstLoopStartTime_ = firstLoopStartTime;
		}
		
		public int getStartPos() { return startPos_; }
		public int getEndPos() { return endPos_; }
		public double getTime() { return time_; }
		public double getFirstLoopStartTime() { return firstLoopStartTime_; }
		public void setNext(Element next) { next_ = next; }
		public Element toNext(Env env) {
			env.currentTime_ += time_;
			return next_;
		}
		public boolean isParent(MMLParser mmlParser) {
			return MMLParser.this == mmlParser;
		}
	}
	
	public class Note extends Element {
		private double freqs_;
		private int kind_;
		private float volume_;
		
		public Note(double firstLoopStartTime, int startPos, int endPos, double freqs, double time, int kind, float volume) {
			super(firstLoopStartTime, startPos, endPos, time);
			freqs_ = freqs;
			kind_ = kind;
			volume_ = volume;
		}
		
		public double getFreqs() { return freqs_; }
		public void setFreqs(double freqs) { freqs_ = freqs; }
		public int getKind() { return kind_; }
		public float getVolume() { return volume_; }
	}
	
	public class LoopStart extends Element {
		private int count_;
		
		public LoopStart(double firstLoopStartTime, int startPos, int endPos, int count) {
			super(firstLoopStartTime, startPos, endPos, 0);
			count_ = count;
		}
		
		public int getCount() { return count_; }
		public Element toNext(Env env) {
			env.push(count_);
			return next_;
		}
	}
	
	public class LoopEnd extends Element {
		private Element returnElement_;
		
		public LoopEnd(double firstLoopStartTime, int startPos, int endPos, Element returnElement) {
			super(firstLoopStartTime, startPos, endPos, 0);
			returnElement_ = returnElement;
		}
		
		public Element toNext(Env env) {
			int count = env.pop();
			count--;
			if (count > 0) {
				env.push(count);
				return returnElement_.next_;
			}
			return next_;
		}
	}
	
	public class SetTempo extends Element {
		private int tempo_;
		
		public SetTempo(double firstLoopStartTime, int startPos, int endPos, int tempo) {
			super(firstLoopStartTime, startPos, endPos, 0);
			tempo_ = tempo;
		}
		
		public int getTempo() { return tempo_; }
	}
}
