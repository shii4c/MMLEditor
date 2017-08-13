package audio;

public class SimpleSinAudio extends IAudioStream {
	private double startTime_;
	private double freq_;
	private double time_;
	private int currentFrame_;

	public SimpleSinAudio(double freq, double startTime, double time, MMLParser.Note note) {
		freq_ = freq;
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
		for (int i = 0; i < frameN; i++) {
			double t = currentTime + i / rate;
			if (currentTime > 0.5) { t = t + Math.sin((t - 0.5) * 32) * ((currentTime > 1.5 ? 1 : (t - 0.5)) / 2048); }
			double p = Math.sin(t * freq_ * Math.PI * 2) * 1024;
			data[offset + i * 2] = (float)p;
			data[offset + i * 2 + 1] = (float)p;
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
