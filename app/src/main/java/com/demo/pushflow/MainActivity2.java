package com.demo.pushflow;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity2 extends AppCompatActivity implements View.OnClickListener {

    String url = "rtmp://sendtc3.douyu.com/live/4375298rkPm2TJxg?wsSecret=fd53e22ab6a04c8349124491994cce4a&wsTime=606a78ef&wsSeek=off&wm=0&tw=0&roirecognition=0&record=flv&origin=tct";

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private ScreenLive screenLive;

    private Button startlive;
    private Button stoplive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();

        initView();
    }

    public boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            }, 1);

        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection
                    (resultCode, data);
            screenLive = new ScreenLive();
            screenLive.startLive(url, mediaProjection);
        }
    }

    private void initView() {
        startlive = (Button) findViewById(R.id.startlive);
        stoplive = (Button) findViewById(R.id.stoplive);

        startlive.setOnClickListener(this);
        stoplive.setOnClickListener(this);
    }

    private void startLive() {
        this.mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 100);
    }

    private void stopLive() {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startlive:
                startLive();
                break;
            case R.id.stoplive:
                stopLive();
                break;
        }
    }
}