package audio;

public class ZeroWaveAudio extends IAudioStream {
	private double startTime_;
	private double time_;
	private int currentFrame_;

	public ZeroWaveAudio(double startTime, double time, MMLParser.Note note) {
		startTime_ = startTime;
		time_ = time;
		note_ = note;
	}

	@Override
	public int readFloat(float[] data, int offset, int frameN) {
		double rate = getSamplingRate();
		double currentTime = currentFrame_ / rate;
		int restFrame = (int)((time_ - currentTime) * rate);
		if (restFrame <= 0) { return 0; }
		if (restFrame < frameN) { frameN = restFrame; }
		int n = frameN * 2;
		for (int i = 0; i < n; i++) {
			data[offset++] = 0;
		}
		currentFrame_ += frameN;
		return frameN;
	}

	@Override
	public int getChannels() {
		return 2;
	}

	@Override
	public int getSamplingRate() {
		return 44100;
	}

	@Override
	public double getStartTime() {
		return startTime_;
	}
	
}
