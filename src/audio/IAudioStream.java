package audio;

public abstract class IAudioStream implements Comparable<IAudioStream> {
	protected MMLParser.Note note_;
	public abstract int readFloat(float[] data, int offset, int frameN);
	public abstract int getChannels();
	public abstract int getSamplingRate();
	public abstract double getStartTime();
	public double getEndTime() {
		if (note_ != null) { return getStartTime() + note_.getTime(); }
		return getStartTime();
	}
	
	public int compareTo(IAudioStream obj) {
		double thisStartTime = getStartTime();
		double otherStartTime = obj.getStartTime();
		if (thisStartTime > otherStartTime) { return 1; }
		if (thisStartTime < otherStartTime) { return -1; }
		return 0;
	}
	
	public MMLParser.Note getNote() { return note_; }
	
	public String toString() {
		return "AudioStream startTime=" + getStartTime() + " endTIme=" + getEndTime();
	}
}
