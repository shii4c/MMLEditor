package audio;

import java.util.ArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;


public class MMLPlayer2 {
	private volatile boolean stopRequested_;
	private ArrayList<IAudioStream> recentAddedStreams_ = new ArrayList<>();
	private SourceDataLine dataLine;
	private MMLParser.Note[] currentPlayingNotes_;
	private double startTime_;
	
	public MMLPlayer2() {
	}
	
	public void play(MMLParser[] mmlList, double startTime, double endTime) throws LineUnavailableException {
		stopRequested_ = false;
		currentPlayingNotes_ = new MMLParser.Note[mmlList.length];
		MMLReader[] mmlReaders = new MMLReader[mmlList.length];
		for (int i = 0; i < mmlList.length; i++) {
			mmlReaders[i] = new MMLReader(mmlList[i]);
		}
		
		Mixer mixer = new Mixer();
		mixer.setStartTime(startTime);
		startTime_ = startTime;
		
		byte[] buff = new byte[2 * 1024 * 2];
		float[] fBuff = new float[1024 * 2];

		AudioFormat audioFormat = new AudioFormat(44100, 16, 2, true, false);
		dataLine = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, audioFormat));
		dataLine.open(audioFormat);
		dataLine.start();

		long currentFrames = (long)(startTime * mixer.getSamplingRate());
		while(!stopRequested_) {
			double reqTime = (double)currentFrames / mixer.getSamplingRate() + 1;
			for (MMLReader mmlReader : mmlReaders) {
				while(mmlReader.getLastStartTime() < reqTime) {
					IAudioStream stream = mmlReader.nextStream();
					if (stream == null) { break; }
					if (stream.getStartTime() < startTime - 0.001) { continue; }
					if (stream.getEndTime() >= endTime + 0.001) { break; }
					mixer.add(stream);
					synchronized(this) {
						recentAddedStreams_.add(stream);
					}
				}
			}
			int readFrame = mixer.readFloat(fBuff, 0, 1024);
			//System.out.println(readFrame);
			if (readFrame == 0) { break; }
			for (int i = 0; i < readFrame; i++) {
				int val = powerToShort(fBuff[i * 2]);
				buff[i * 4] = (byte)val;
				buff[i * 4 + 1] = (byte)(val >> 8);

				val = powerToShort(fBuff[i * 2 + 1]);
				buff[i * 4 + 2] = (byte)val;
				buff[i * 4 + 3] = (byte)(val >> 8);
			}
			dataLine.write(buff, 0, readFrame * 2 * 2);
			currentFrames += readFrame;
		}
		if (!stopRequested_) {
			// 再生が終わるまで待つ
			dataLine.drain();
		}
		// closeする
		dataLine.close();
	}
	
	public MMLParser.Note[] getCurrentPlayingNotes() {
		if (dataLine == null) { return null; }
		double playingTime = dataLine.getMicrosecondPosition() / 1000000.0 + startTime_;
		int index = 0;
		//System.out.println(recentAddedStreams_.size());
		synchronized(this) {
			for (int i = recentAddedStreams_.size() - 1; i >= 0; i--) {
				IAudioStream stream = recentAddedStreams_.get(i);
				if (stream.getEndTime() + 0.0001 <= playingTime) {
					recentAddedStreams_.remove(i);
				} else
				if (stream.getStartTime() + 0.0001 < playingTime) {
					if (index < currentPlayingNotes_.length) { currentPlayingNotes_[index++] = stream.getNote(); }
				} 
			}
		}
		for (int i = index; i < currentPlayingNotes_.length; i++) { currentPlayingNotes_[i] = null; }
		return currentPlayingNotes_;
	}
	
	public void requestStop() {
		stopRequested_ = true;
	}
	
	private int powerToShort(double val) {
		if (val > 30000) {
			return (int)(32767 - 2767 / (val - 30000 + 1));
		}
		if (val < -30000) {
			return (int)(-32767 + 2767 / (-val - 30000 + 1));
		}
		return (int)val;
	}
	
	private class MMLReader {
		private MMLParser.Element currentElem_;
		private double lastStartTime_;
		private MMLParser.Env env_ = new MMLParser.Env();
		
		public MMLReader(MMLParser mml) {
			if (mml.getMMLElements().size() > 0) {
				currentElem_ = mml.getMMLElements().get(0);
			}
		}
		
		public double getLastStartTime() { return lastStartTime_; }
		
		public IAudioStream nextStream() {
			IAudioStream stream = nextStreamInner();
			if (stream != null) {
				lastStartTime_ = stream.getStartTime();
			}
			return stream;
		}
		
		private IAudioStream nextStreamInner() {
			while(currentElem_ != null) {
				if (currentElem_ instanceof MMLParser.Note) {
					MMLParser.Note note = (MMLParser.Note)currentElem_;
					double len = note.getTime();
					double f = note.getFreqs();
					double currentTime = env_.getCurrentTime();
					int soundKind = note.getKind();
					float volume = note.getVolume();
					IAudioStream streamToAdd;
					if (f == 0) {
						streamToAdd = new ZeroWaveAudio(currentTime, len, note);
					} else if (soundKind == 1) {
						streamToAdd = new AudioGainFilter(new SawWaveAudio(f, currentTime, len + 0.25, note), volume, 0.01f, (float)(0.0f), 4f, 0.01f);
					} else if (soundKind == 2) {
						streamToAdd = new AudioGainFilter(new SimpleSinAudio(f, currentTime, len + 0.25, note), volume, 0.05f, (float)(len - 0.2), 4f, 0.01f);
					} else {
						streamToAdd = new AudioGainFilter(new SawWaveAudio(f, currentTime, len + 0.25, note), volume, 0.1f, (float)(len - 0.2), 4f, 0.01f);
					}
					currentElem_ = currentElem_.toNext(env_);
					return streamToAdd;
				}
				currentElem_ = currentElem_.toNext(env_);
			}
			return null;
		}
		
	}

}
