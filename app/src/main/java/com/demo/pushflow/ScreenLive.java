package com.demo.pushflow;

import android.media.projection.MediaProjection;

import com.demo.pushflow.entity.RTMPPackage;
import com.demo.pushflow.util.AudioCodec;
import com.demo.pushflow.util.VideoCodec;

import java.util.concurrent.LinkedBlockingQueue;

public class ScreenLive extends Thread {

    static {
        System.loadLibrary("native-lib");
    }

    private boolean isLiving;
    private LinkedBlockingQueue<RTMPPackage> queue = new LinkedBlockingQueue<>();
    private String url;
    private MediaProjection mediaProjection;

    public void startLive(String url, MediaProjection mediaProjection) {
        this.url = url;
        this.mediaProjection = mediaProjection;
        LiveTaskManager.getInstance().execute(this);
    }


    public void addPackage(RTMPPackage rtmpPackage) {
        if (!isLiving) {
            return;
        }
        queue.add(rtmpPackage);
    }

    @Override
    public void run() {
        //1推送到
        if (!connect(url)) {
            return;
        }

        VideoCodec videoCodec = new VideoCodec(this);
        videoCodec.startLive(mediaProjection);

        AudioCodec audioCodec = new AudioCodec(this);
        audioCodec.startLive();

        isLiving = true;
        while (isLiving) {
            RTMPPackage rtmpPackage = null;
            try {
                rtmpPackage = queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (rtmpPackage.getBuffer() != null && rtmpPackage.getBuffer().length != 0) {
                sendData(rtmpPackage.getBuffer(), rtmpPackage.getBuffer()
                        .length, rtmpPackage.getTms(), rtmpPackage.getType());
            }
        }
    }

    //连接RTMP服务器
    private native boolean connect(String url);

    //发送RTMP Data
    private native boolean sendData(byte[] data, int len, long tms, int type);
}
