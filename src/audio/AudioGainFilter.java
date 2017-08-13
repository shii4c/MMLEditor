package audio;

public class AudioGainFilter extends IAudioStream {
	private IAudioStream stream_;
	private int samplingRate_, channels_;
	private int preEndTime_, midEndTime_, postEndTime_;
	private double postRate_, currentRate_ = 1;;
	private int currentFrame_;
	private int endFrame_;
	private float volume_;

	public AudioGainFilter(IAudioStream stream, float volume, float preTime, float midTime, float postTime, float postRate) {
		stream_ = stream;
		samplingRate_ = stream.getSamplingRate();
		channels_ = stream.getChannels();
		volume_ = volume;
		preEndTime_ = (int)(preTime * samplingRate_);
		midEndTime_ = (int)((preTime + midTime) * samplingRate_);
		postEndTime_ = (int)((preTime + midTime + postTime) * samplingRate_);
		postRate_ = Math.pow(postRate, 1.0 / samplingRate_);
		
		endFrame_ = (int)((preTime + midTime + postTime) * getSamplingRate()); 
	}

	@Override
	public int readFloat(float[] data, int offset, int frameN) {
		if (currentFrame_ + frameN > endFrame_) {
			frameN = endFrame_ - currentFrame_;
		}
		if (frameN <= 0) { return 0; }
		
		int readFrameN = stream_.readFloat(data, offset, frameN);
		if (readFrameN > 0) {
			for (int i = 0; i < readFrameN; i++) {
				float rate = 1;
				if (currentFrame_ < preEndTime_) {
					rate = (float)currentFrame_ / preEndTime_;
				} else if (currentFrame_ < midEndTime_) {
					
				} else {
					rate = (float)currentRate_;
					currentRate_ *= postRate_;
				}
				for (int j = 0; j < channels_; j++) {
					data[offset++] *= rate * volume_;
				}
				currentFrame_++;
			}
		}
		return readFrameN;
	}

	@Override
	public int getChannels() {
		return stream_.getChannels();
	}

	@Override
	public int getSamplingRate() {
		return stream_.getSamplingRate();
	}

	@Override
	public double getStartTime() {
		return stream_.getStartTime();
	}
	
	@Override
	public double getEndTime() {
		return stream_.getEndTime();
	}
	
	@Override
	public MMLParser.Note getNote() {
		return stream_.getNote();
	}
}
