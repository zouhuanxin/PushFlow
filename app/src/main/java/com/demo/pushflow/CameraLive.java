package com.demo.pushflow;


import com.demo.pushflow.entity.RTMPPackage;

import java.util.concurrent.LinkedBlockingQueue;

public class CameraLive extends Thread {

    static {
        System.loadLibrary("native-lib");
    }

    private boolean isLiving;
    private LinkedBlockingQueue<RTMPPackage> queue = new LinkedBlockingQueue<>();
    private String url;

    public void startLive(String url) {
        this.url = url;
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
    public native boolean connect(String url);

    //发送RTMP Data
    public native boolean sendData(byte[] data, int len, long tms, int type);

    //连接RTMP服务器
    public native boolean disconnect();

    public native byte[] readData();
}
