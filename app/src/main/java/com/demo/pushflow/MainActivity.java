package com.demo.pushflow;

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

import com.demo.pushflow.util.AACEncoder;
import com.demo.pushflow.util.AVCEncoder;
import com.demo.pushflow.util.Mp4Muxer;
import com.demo.pushflow.util.PermissionUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, TextureView.SurfaceTextureListener {
    private Camera camera;
    private long startPts = 0, timeStamp = 0;
    private AACEncoder mAACEncoder;
    private AVCEncoder avcEncoder;
    private boolean startRecord;
    private Mp4Muxer mMp4Muxer = new Mp4Muxer("/sdcard/b.mp4");
    private MediaFormat vFormat, aFormat;

    private Button startlive;
    private Button stoplive;
    private TextureView tureview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new PermissionUtils().verifyStoragePermissions(this, null);
        setContentView(R.layout.activity_main);
        initView();
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
                startRecoding(camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
                break;
            case R.id.stoplive:
                stopRecoding();
                break;
        }
    }

    private void stopRecoding() {
        mAACEncoder.stop();
        avcEncoder.stop();
        mMp4Muxer.stop();
    }

    private void startRecoding(int width, int height) {
        try {
            initAudioCoder();
            initVideoCoder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initVideoCoder() {
        avcEncoder = new AVCEncoder(camera);
        try {
            avcEncoder.setVideoEnncoderListener(new AVCEncoder.VideoEnncoderListener() {
                @Override
                public void getVideoBuffer(MediaCodec mediaCodec, int encoderStatus, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, byte[] data) {
                    if (System.currentTimeMillis() - timeStamp >= 2000) {
                        Bundle params = new Bundle();
                        //立即刷新 让下一帧是关键帧
                        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                        mediaCodec.setParameters(params);
                        timeStamp = System.currentTimeMillis();
                    }
                    if (startPts == 0) {
                        startPts = bufferInfo.presentationTimeUs;
                    }
                    long pts = (bufferInfo.presentationTimeUs) - startPts;
                    bufferInfo.presentationTimeUs = pts;
                    mMp4Muxer.writeVideoData(byteBuffer, bufferInfo);
                    mediaCodec.releaseOutputBuffer(encoderStatus, false);
                }

                @Override
                public void getOutputFormat(MediaFormat format) {
                    vFormat = format;
                    startMuxer();
                }
            });
            avcEncoder.prepare(camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height);
            avcEncoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void initAudioCoder() {
        mAACEncoder = new AACEncoder();
        try {
            mAACEncoder.setAudioEnncoderListener(new AACEncoder.AudioEnncoderListener() {
                @Override
                public void getAudioBuffer(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, byte[] data) {
                    if (startRecord && startPts != 0) {
                        long pts = System.nanoTime() / 1000 - startPts;
                        bufferInfo.presentationTimeUs = pts;
                        mMp4Muxer.writeAudioData(byteBuffer, bufferInfo);
                    }
                }

                @Override
                public void getOutputFormat(MediaFormat format) {
                    aFormat = format;
                    startMuxer();
                }
            });
            mAACEncoder.prepare();
            mAACEncoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startMuxer() {
        if (startRecord) {
            return;
        }
        if (vFormat != null && aFormat != null) {
            mMp4Muxer.addVideoTrack(vFormat);
            mMp4Muxer.addAudioTrack(aFormat);
            mMp4Muxer.start();
            startRecord = true;
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