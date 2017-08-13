package audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

public class Mixer extends IAudioStream {
	private ArrayList<IAudioStream> sourceStreams_ = new ArrayList<>();
	private ArrayList<IAudioStream> activeStreams_ = new ArrayList<>();
	private int nextSourceIndex_ = 0;
	private long nextSourceStartFrame_;
	private long currentFrame_;
	private int samplingRate_ = 44100;
	private boolean sorted_ = false;

	private static final int buffFrameN_ = 4096;
	private float[] floatBuff_ = new float[buffFrameN_ * 2];

	public void setStartTime(double startTime) {
		currentFrame_ = (long)(startTime * samplingRate_);
	}
	
	public void add(IAudioStream stream) {
		sourceStreams_.add(stream);
		sorted_ = false;
	}

	public void start() {
	}

	private void prepareNext() {
		if (!sorted_) {
			Collections.sort(sourceStreams_);
			sorted_ = true;
		}
		//double currentTime = (double)currentFrame_ / samplingRate_;
		while(true) {
			if (nextSourceIndex_ >= sourceStreams_.size()) {
				nextSourceStartFrame_ = -1;
				break;
			}
			IAudioStream stream = sourceStreams_.get(nextSourceIndex_);
			if ((long)(stream.getStartTime() * samplingRate_) > currentFrame_) {
				nextSourceStartFrame_ = (long)(stream.getStartTime() * samplingRate_);
				break;
			}
			nextSourceIndex_++;
			activeStreams_.add(stream);
		}
	}

	@Override
	public int readFloat(float[] data, int offset, int frameN) {
		if (activeStreams_.size() == 0 && nextSourceIndex_ >= sourceStreams_.size()) {
			return 0;
		}
		if (nextSourceStartFrame_ >= 0 && nextSourceStartFrame_ <= currentFrame_) { prepareNext(); }
		int reqFrameN = nextSourceStartFrame_ >= 0 ? (int)(nextSourceStartFrame_ - currentFrame_) : buffFrameN_;
		if (reqFrameN > buffFrameN_) { reqFrameN = buffFrameN_; }
		if (reqFrameN > frameN) { reqFrameN = frameN; }
		if (reqFrameN <= 0) {
			return 0;
		}

		for (int i = 0; i < reqFrameN * 2; i++) { data[offset + i] = 0; }
		ListIterator<IAudioStream> ite = activeStreams_.listIterator();
		while(ite.hasNext()) {
			IAudioStream stream = ite.next();
			int readFrameN = stream.readFloat(floatBuff_, 0, reqFrameN);
			for (int i = 0; i < readFrameN * 2; i++) { data[offset + i] += floatBuff_[i]; }
			if (readFrameN == 0) { ite.remove(); }
		}
		currentFrame_ += reqFrameN;

		return reqFrameN;
	}

	@Override
	public int getChannels() { return 2; }
	@Override
	public int getSamplingRate() { return samplingRate_; }
	@Override
	public double getStartTime() { return 0; }

}
