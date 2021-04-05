package com.demo.pushflow;

import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.demo.pushflow.entity.RTMPPackage;
import com.demo.pushflow.util.AACEncoder;
import com.demo.pushflow.util.AACEncoder3;
import com.demo.pushflow.util.AVCEncoder;
import com.demo.pushflow.util.AVCEncoder3;
import com.demo.pushflow.util.Mp4Muxer;
import com.demo.pushflow.util.PermissionUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity3 extends AppCompatActivity implements View.OnClickListener, TextureView.SurfaceTextureListener {
    private Camera camera;
    private Button startlive;
    private Button stoplive;
    private TextureView tureview;

    private AACEncoder3 aacEncoder3;
    private AVCEncoder3 avcEncoder3;
    private CameraLive cameraLive;

    private long startVideoTime, startAudioTime;

    private String url = "rtmp://sendtc3.douyu.com/live/4375298rkPm2TJxg?wsSecret=bf741e6c7ea5552fac796ab959c1df95&wsTime=606a7f69&wsSeek=off&wm=0&tw=0&roirecognition=0&record=flv&origin=tct";

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
        tureview = (TextureView) findViewById(R.id.tureview);

        tureview.setSurfaceTextureListener(this);
        startlive.setOnClickListener(this);
        stoplive.setOnClickListener(this);
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