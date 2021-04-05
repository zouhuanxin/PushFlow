package com.demo.pushflow.util;

import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AVCEncoder3 implements Runnable {
    private boolean isVideoing = true;
    private MediaCodec VideomediaCodec;
    private Thread mThread;
    private VideoEnncoderListener videoEnncoderListener;
    private Camera camera;
    private byte[] datas;
    private HandlerThread handlerThread = new HandlerThread("video2");
    private Handler handler;
    private long timeStamp;


    public AVCEncoder3(Camera camera) {
        this.camera = camera;
    }

    public void setVideoEnncoderListener(VideoEnncoderListener videoEnncoderListener) {
        this.videoEnncoderListener = videoEnncoderListener;
    }

    public void prepare(int width, int height) throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        //色彩空间
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        //码率
        format.setInteger(MediaFormat.KEY_BIT_RATE, 400_000);
        //帧率
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        //关键帧间隔
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        VideomediaCodec = MediaCodec.createEncoderByType("video/avc");
        VideomediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

    }

    public void start() throws InterruptedException {
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0:
                        readOutputData();
                        break;
                }
            }
        };
        VideomediaCodec.start();

        if (mThread != null && mThread.isAlive()) {
            isVideoing = false;
            mThread.join();
        }
        isVideoing = true;
        mThread = new Thread(this);
        mThread.start();
    }

    private ByteBuffer getInputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return VideomediaCodec.getInputBuffer(index);
        } else {
            return VideomediaCodec.getInputBuffers()[index];
        }
    }

    private ByteBuffer getOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return VideomediaCodec.getOutputBuffer(index);
        } else {
            return VideomediaCodec.getOutputBuffers()[index];
        }
    }

    private void readOutputData() {
        int index = VideomediaCodec.dequeueInputBuffer(0);
        if (index >= 0) {
            ByteBuffer inputBuffer = VideomediaCodec.getInputBuffer(index);
            inputBuffer.clear();
            inputBuffer.put(datas, 0, datas.length);
            //填充数据后再加入队列
            VideomediaCodec.queueInputBuffer(index, 0, datas.length, System.nanoTime() / 1000, 0);
        }
        while (true) {
            //获得输出缓冲区（编码后的数据从输出缓冲区获得）
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int encoderStatus = VideomediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //稍后重试
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (videoEnncoderListener != null) {
                    videoEnncoderListener.getOutputFormat(VideomediaCodec.getOutputFormat());
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                //可以忽略
            } else {
                ByteBuffer encodedData = VideomediaCodec.getOutputBuffer(encoderStatus);
                byte[] outData = new byte[bufferInfo.size];
                encodedData.get(outData);
                VideomediaCodec.releaseOutputBuffer(encoderStatus, false);
                if (videoEnncoderListener != null) {
                    videoEnncoderListener.getVideoBuffer(VideomediaCodec, encoderStatus, encodedData, bufferInfo, outData);
                }
            }
        }
    }

    public void stop() {
        try {
            isVideoing = false;
            mThread.join();
            VideomediaCodec.stop();
            VideomediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (isVideoing) {
                    datas = data;
                    Message message = new Message();
                    message.what = 0;
                    handler.sendMessage(message);
                }
            }
        });
    }

    public interface VideoEnncoderListener {
        void getVideoBuffer(MediaCodec mediaCodec, int encoderStatus, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, byte[] data);

        void getOutputFormat(MediaFormat format);
    }
}
