package com.demo.pushflow.util;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AACEncoder implements Runnable {
    private String TAG = getClass().getSimpleName();
    private String mime = "audio/mp4a-latm";
    private AudioRecord mRecorder;
    private MediaCodec mEnc;
    private MediaFormat mMediaFormat;
    private int rate = 128000;//9600

    //录音设置
    private int sampleRate = 8000;   //采样率，默认44.1k
    private int channelCount = 1;     //音频采样通道，默认2通道
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;        //通道设置，默认立体声
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;     //设置采样数据格式，默认16比特PCM
    private FileOutputStream fos;

    private boolean isRecording;
    private Thread mThread;
    private int bufferSize;

    private String mSavePath;
    private AudioEnncoderListener audioEnncoderListener;

    public AACEncoder() {
    }

    public void setAudioEnncoderListener(AudioEnncoderListener audioEnncoderListener) {
        this.audioEnncoderListener = audioEnncoderListener;
    }

    public void setSavePath(String path) {
        this.mSavePath = path;
    }


    public void prepare() throws IOException {
        if (isWriteLocaAAC) {
            fos = new FileOutputStream(mSavePath);
        }
        //音频编码相关
        mMediaFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
        mMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, rate);
        byte[] data = new byte[]{(byte) 0x11, (byte) 0x90};

        ByteBuffer mCSD0 = ByteBuffer.wrap(data);
//        mCSD0.clear();

//        mCSD1 = ByteBuffer.wrap(header_pps);
//        mCSD1.clear();
        mMediaFormat.setByteBuffer("csd-0", mCSD0);
//        mMediaFormat.setByteBuffer("csd-1", mCSD1);

        mEnc = MediaCodec.createEncoderByType(mime);
        mEnc.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        //音频录制相关
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                audioFormat, bufferSize);
    }

    public MediaFormat getMediaFormat() {
        return mMediaFormat;
    }

    public void start() throws InterruptedException {
        mEnc.start();
        mRecorder.startRecording();
        if (mThread != null && mThread.isAlive()) {
            isRecording = false;
            mThread.join();
        }
        isRecording = true;
        mThread = new Thread(this);
        mThread.start();
    }

    private ByteBuffer getInputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mEnc.getInputBuffer(index);
        } else {
            return mEnc.getInputBuffers()[index];
        }
    }

    private ByteBuffer getOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mEnc.getOutputBuffer(index);
        } else {
            return mEnc.getOutputBuffers()[index];
        }
    }

    private boolean isWriteLocaAAC = false;

    private void readOutputData() throws IOException {
        int index = mEnc.dequeueInputBuffer(-1);
        long pts = System.nanoTime() / 1000;
        if (index >= 0) {
            final ByteBuffer buffer = getInputBuffer(index);
            buffer.clear();
            int length = mRecorder.read(buffer, bufferSize);
            if (length > 0) {
                mEnc.queueInputBuffer(index, 0, length, pts, 0);//System.nanoTime() / 1000
//                Log.d(TAG, "queueInputBuffer:  pts:"+pts);
            } else {
                Log.e(TAG, "length-->" + length);
            }
        }
        MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
        int outIndex;
        do {
            outIndex = mEnc.dequeueOutputBuffer(mInfo, 0);
//            Log.e(TAG, "audio flag---->" + mInfo.flags + "/" + outIndex);
            if (outIndex >= 0) {
                ByteBuffer buffer = getOutputBuffer(outIndex);
//                if (isWriteLocaAAC) {
//                    buffer.position(mInfo.offset);
//                    byte[] temp = new byte[mInfo.size + 7];
//                    buffer.get(temp, 7, mInfo.size);
//                    addADTStoPacket(temp, temp.length);
//                    Log.d(TAG, "readOutputData: temp.length-->" + temp.length);
//                    fos.write(temp);
//                }
                byte[] outData = new byte[mInfo.size];
                buffer.get(outData);
                if (audioEnncoderListener != null) {
                    audioEnncoderListener.getAudioBuffer(buffer, mInfo, outData);
                }
//                Log.d(TAG, "audio: pts"+mInfo.presentationTimeUs);
                mEnc.releaseOutputBuffer(outIndex, false);
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {

            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "readOutputData: " + mEnc.getOutputFormat());
                if (audioEnncoderListener != null) {
                    audioEnncoderListener.getOutputFormat(mEnc.getOutputFormat());
                }

            }
        } while (outIndex >= 0);
    }

    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 11;  //44.1KHz--4  这个参数跟采样率有关sampleRate，8000-->11 16000-->8 44100-->4
        int chanCfg = channelCount;  //CPE  这个参数跟通道数有关channelCount   chanCfg = 这个参数跟通道数有关channelCount
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    /**
     * 停止录制
     */
    public void stop() {
        try {
            isRecording = false;
            mThread.join();
            mRecorder.stop();
            mEnc.stop();
            mEnc.release();
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (isRecording) {
            try {
                readOutputData();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface AudioEnncoderListener {
        void getAudioBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, byte[] data);

        void getOutputFormat(MediaFormat format);
    }

}
