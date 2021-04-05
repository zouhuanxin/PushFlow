package com.demo.pushflow;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Base64;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.demo.pushflow.entity.RTMPPackage;
import com.demo.pushflow.util.AACEncoder3;
import com.demo.pushflow.util.AVCEncoder3;
import com.demo.pushflow.util.PermissionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity3 extends AppCompatActivity implements View.OnClickListener, TextureView.SurfaceTextureListener {
    private Camera camera;
    private Button startlive;
    private Button stoplive;
    private Button pullflow;
    private TextureView tureview;

    private AACEncoder3 aacEncoder3;
    private AVCEncoder3 avcEncoder3;
    private CameraLive cameraLive;

    private long startVideoTime, startAudioTime;

    //private String url = "rtmp://sendtc3.douyu.com/live/4375298rsIyZjaUt?wsSecret=e14d3a1a21d0e1793f8f0dd9c0e8ae0f&wsTime=606afdd9&wsSeek=off&wm=0&tw=0&roirecognition=0&record=flv&origin=tct";
    private String url = "rtmp://192.168.123.196/live/livestrea2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new PermissionUtils().verifyStoragePermissions(this, null);
        setContentView(R.layout.activity_main);
        initView();
        cameraLive = new CameraLive();
        cameraLive.startLive(url);
    }

    private void initView() {
        startlive = (Button) findViewById(R.id.startlive);
        stoplive = (Button) findViewById(R.id.stoplive);
        pullflow = (Button) findViewById(R.id.pullflow);
        tureview = (TextureView) findViewById(R.id.tureview);

        tureview.setSurfaceTextureListener(this);
        startlive.setOnClickListener(this);
        stoplive.setOnClickListener(this);
        pullflow.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startlive:
                startRecoding();
                break;
            case R.id.stoplive:
                stopRecoding();
                break;
            case R.id.pullflow:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (true){
                            try {
                                tt(cameraLive.readData());
                                Thread.sleep(25);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
                break;
        }
    }

    private void tt(byte[] content) {
        try {
            byte[] mp3SoundByteArray = Base64.decode(content, Base64.DEFAULT);// 将字符串转换为byte数组
            File tempMp3 = File.createTempFile("badao", ".mp3");
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(mp3SoundByteArray);
            fos.close();
            MediaPlayer mediaPlayer = new MediaPlayer();
            FileInputStream fis = new FileInputStream(tempMp3);
            mediaPlayer.setDataSource(fis.getFD());

            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException ex) {
            String s = ex.toString();
            ex.printStackTrace();
        }
    }


    private void stopRecoding() {
        aacEncoder3.stop();
        avcEncoder3.stop();
        cameraLive.disconnect();
    }

    private void startRecoding() {
        initVideo();
        initAudio();
    }

    private void initVideo() {
        avcEncoder3 = new AVCEncoder3(camera);
        avcEncoder3.setVideoEnncoderListener(new AVCEncoder3.VideoEnncoderListener() {
            @Override
            public void getVideoBuffer(MediaCodec mediaCodec, int encoderStatus, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, byte[] data) {
                if (startVideoTime == 0) {
                    startVideoTime = bufferInfo.presentationTimeUs / 1000;
                }
                RTMPPackage rtmpPackage = new RTMPPackage(data, (bufferInfo.presentationTimeUs / 1000) - startVideoTime);
                rtmpPackage.setType(RTMPPackage.RTMP_PACKET_TYPE_VIDEO);
                cameraLive.addPackage(rtmpPackage);
            }

            @Override
            public void getOutputFormat(MediaFormat format) {

            }
        });
        try {
            avcEncoder3.prepare(camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
            avcEncoder3.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initAudio() {
        aacEncoder3 = new AACEncoder3();

        RTMPPackage rtmpPackage = new RTMPPackage();
        byte[] audioSpec = {0x12, 0x08};
        rtmpPackage.setBuffer(audioSpec);
        rtmpPackage.setType(RTMPPackage.RTMP_PACKET_TYPE_AUDIO_HEAD);
        rtmpPackage.setTms(0);
        cameraLive.addPackage(rtmpPackage);

        aacEncoder3.setAudioEnncoderListener(new AACEncoder3.AudioEnncoderListener() {
            @Override
            public void getAudioBuffer(MediaCodec mediaCodec, int status, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, byte[] data) {
                RTMPPackage rtmpPackage = new RTMPPackage();
                rtmpPackage.setBuffer(data);
                rtmpPackage.setType(RTMPPackage.RTMP_PACKET_TYPE_AUDIO_DATA);
                if (startAudioTime == 0) {
                    startAudioTime = bufferInfo.presentationTimeUs / 1000;
                }
                rtmpPackage.setTms(bufferInfo.presentationTimeUs / 1000 - startAudioTime);
                cameraLive.addPackage(rtmpPackage);
            }

            @Override
            public void getOutputFormat(MediaFormat format) {

            }
        });
        try {
            aacEncoder3.prepare();
            aacEncoder3.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        try {
            if (camera != null) {
                camera.stopPreview();
            }
            camera = Camera.open();
            camera.setPreviewTexture(surface);
            camera.setDisplayOrientation(90);
            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        camera.stopPreview();
        camera.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }
}