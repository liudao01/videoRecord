/************************************************************
 * * EaseMob CONFIDENTIAL
 * __________________
 * Copyright (C) 2013-2014 EaseMob Technologies. All rights reserved.
 * <p/>
 * NOTICE: All information contained herein is, and remains
 * the property of EaseMob Technologies.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from EaseMob Technologies.
 */
package www.videt.test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import www.videt.test.utils.AutoFocusManage;
import www.videt.test.utils.EaseCommonUtils;
import www.videt.test.utils.LogUtils;
import www.videt.test.utils.PathUtil;
import www.videt.test.utils.Utils;


public class RecorderVideoActivity extends Activity implements
        OnClickListener, SurfaceHolder.Callback, OnErrorListener,
        OnInfoListener {
    private static final String TAG = "RecorderVideoActivity";
    private final static String CLASS_LABEL = "RecordActivity";
    private PowerManager.WakeLock mWakeLock;
    private ImageView btnStart;// 开始录制按钮
    private ImageView btnStop;// 停止录制按钮
    private MediaRecorder mediaRecorder;// 录制视频的类
    private VideoView mVideoView;// 显示视频的控件
    String localPath = "";// 录制的视频路径
    private Camera mCamera;
    // 预览的宽高
//    private int previewWidth = 1080;
//    private int previewHeight = 1920;
//    private int previewWidth = 480;
//    private int previewHeight = 480;
    private int previewWidth = 640;
    private int previewHeight = 480;

//    private int defaultPreviewWidth = 1920;
//    private int defaultPreviewHeight = 1080;
    private int defaultPreviewWidth = 1280;
    private int defaultPreviewHeight = 720;

    private Chronometer chronometer;
    private int frontCamera = 0;// 0是后置摄像头，1是前置摄像头
    private Button btn_switch;
    Parameters cameraParameters = null;
    private SurfaceHolder mSurfaceHolder;
    int defaultVideoFrameRate = -1;
    AutoFocusManage autoFocusManage;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        // 选择支持半透明模式，在有surfaceview的activity中使用
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.em_recorder_activity);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                CLASS_LABEL);
        mWakeLock.acquire();
        initViews();


    }

    private void initViews() {
        btn_switch = (Button) findViewById(R.id.switch_btn);
        btn_switch.setOnClickListener(this);
//        btn_switch.setVisibility(View.VISIBLE);
        mVideoView = (VideoView) findViewById(R.id.mVideoView);
        btnStart = (ImageView) findViewById(R.id.recorder_start);
        btnStop = (ImageView) findViewById(R.id.recorder_stop);
        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        mSurfaceHolder = mVideoView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        chronometer = (Chronometer) findViewById(R.id.chronometer);
    }

    public void back(View view) {
        releaseRecorder();
        releaseCamera();
        finish();
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            // 获取唤醒锁,保持屏幕常亮
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                    CLASS_LABEL);
            mWakeLock.acquire();
        }
//		if (!initCamera()) {
//			showFailDialog();
//		}
    }

    private boolean initCamera() {
        try {
            if (frontCamera == 0) {
                mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
            } else {
                mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
            }
            Parameters camParams = mCamera.getParameters();
            mCamera.lock();
            mSurfaceHolder = mVideoView.getHolder();
            mSurfaceHolder.addCallback(this);
            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mCamera.setDisplayOrientation(90);
            autoFocusManage = new AutoFocusManage(this, mCamera);

        } catch (RuntimeException ex) {
            LogUtils.e("video", "init Camera fail " + ex.getMessage());
            return false;
        }

        return true;
    }

    private boolean isPreviewActive = true;//preview是否是活动的。防止preview是inactive时去调用对焦产生异常。

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isPreviewActive) {//preview活动时才能调用自动对焦功能
            //对焦
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    camera.cancelAutoFocus();
                }
            });
        }
        return super.onTouchEvent(event);
    }

//    private void initAutoFocus() {
//
//        if (isPreviewActive && mCamera != null) {
//
//            mCamera.cancelAutoFocus();
//            mCamera.getParameters().setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//            mCamera.autoFocus(new Camera.AutoFocusCallback() {
//                @Override
//                public void onAutoFocus(boolean success, Camera camera) {
//                }
//            });
//        }
//    }

    /**
     * 设置参数
     *
     * @param mRecorder
     * @param filePath  1，花屏主要跟VideoSize有关，将Size调到640*480以上花屏问题可解决,或者录制屏幕为正方形。
     *                  2，清晰度和录制文件大小主要和EncodingBitRate有关，参数越大越清晰，同时录制的文件也越大。
     *                  3，视频文件的流畅度主要跟VideoFrameRate有关，参数越大视频画面越流畅，但实际中跟你的摄像头质量有很大关系。
     */
    private void setMeadiaRecorder(MediaRecorder mRecorder, String filePath) {
        mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int i, int i1) {
                Log.d("-------->", "MediaRecorder.onError");
            }
        });
        mRecorder.setCamera(mCamera); //给Recorder设置Camera对象，保证录像跟预览的方向保持一致
        if (frontCamera == 1) {
            mRecorder.setOrientationHint(90);  //改变保存后的视频文件播放时是否横屏(不加这句，视频文件播放的时候角度是反的)
        } else {
            mRecorder.setOrientationHint(270);  //改变保存后的视频文件播放时是否横屏(不加这句，视频文件播放的时候角度是反的)
        }
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA); // 设置从摄像头采集图像
        mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT); // 设置从麦克风采集声音

        CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);// 设置视频的输出格式 为MP4
//        mRecorder.setOutputFormat(mProfile.fileFormat);
        mRecorder.setAudioEncoder(mProfile.audioCodec);// 设置音频的编码格式
//        mRecorder.setVideoEncoder(mProfile.videoCodec);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);// 设置视频的编码格式
        mRecorder.setOutputFile(filePath);
//        mRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mRecorder.setVideoSize(640, 480);
        //设置录制的视频帧率,必须放在设置编码和格式的后面，否则报错
        mRecorder.setVideoFrameRate(mProfile.videoFrameRate);
        mRecorder.setVideoEncodingBitRate(800 * 1024);//视频码率
        mRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
//        mRecorder.setAudioChannels(mProfile.audioChannels);
        mRecorder.setAudioChannels(1);//设置录制的音频通道数
//        mRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
        mRecorder.setAudioSamplingRate(44100);
//        mRecorder.setMaxDuration(duration * 1000); //设置最大录像时间为10s
    }


    private void handleSurfaceChanged() {
        if (mCamera == null) {
            finish();
            return;
        }
        boolean hasSupportRate = false;
        List<Integer> supportedPreviewFrameRates = mCamera.getParameters()
                .getSupportedPreviewFrameRates();
        if (supportedPreviewFrameRates != null
                && supportedPreviewFrameRates.size() > 0) {
            Collections.sort(supportedPreviewFrameRates);
            for (int i = 0; i < supportedPreviewFrameRates.size(); i++) {
                int supportRate = supportedPreviewFrameRates.get(i);

                if (supportRate == 15) {
                    hasSupportRate = true;
                }

            }
            if (hasSupportRate) {
                defaultVideoFrameRate = 15;
            } else {
                defaultVideoFrameRate = supportedPreviewFrameRates.get(0);
            }

        }
        // 获取摄像头的所有支持的分辨率
        List<Size> resolutionList = Utils.getResolutionList(mCamera);
        if (resolutionList != null && resolutionList.size() > 0) {
            Collections.sort(resolutionList, new Utils.ResolutionComparator());
            Size previewSize = null;
            boolean hasSize = false;
            // 如果摄像头支持640*480，那么强制设为640*480
            for (int i = 0; i < resolutionList.size(); i++) {
                Size size = resolutionList.get(i);
                if (size != null && size.width == defaultPreviewWidth && size.height == defaultPreviewHeight) {
                    previewSize = size;
                    previewWidth = previewSize.width;
                    previewHeight = previewSize.height;
                    hasSize = true;
                    break;
                }
            }
            // 如果不支持设为中间的那个
            if (!hasSize) {
                int mediumResolution = resolutionList.size() / 2;
                if (mediumResolution >= resolutionList.size())
                    mediumResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(mediumResolution);
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
            }

        }


    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_btn:
                switchCamera();
                break;
            case R.id.recorder_start:
                // start recording
                if (!startRecording())
                    return;
                Toast.makeText(this, R.string.The_video_to_start, Toast.LENGTH_SHORT).show();
//                btn_switch.setVisibility(View.INVISIBLE);
                btnStart.setVisibility(View.INVISIBLE);
                btnStart.setEnabled(false);
                btnStop.setVisibility(View.VISIBLE);
                // 重置其他
                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.start();
                break;
            case R.id.recorder_stop:
                btnStop.setEnabled(false);
                // 停止拍摄
                stopRecording();
//                btn_switch.setVisibility(View.VISIBLE);
                chronometer.stop();
                btnStart.setVisibility(View.VISIBLE);
                btnStop.setVisibility(View.INVISIBLE);
                new AlertDialog.Builder(this)
                        .setMessage(R.string.Whether_to_send)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialog.dismiss();
                                        sendVideo(null);

                                    }
                                })
                        .setNegativeButton(R.string.cancel,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        if (localPath != null) {
                                            File file = new File(localPath);
                                            if (file.exists())
                                                file.delete();
                                        }
                                        finish();

                                    }
                                }).setCancelable(false).show();
                break;

            default:
                break;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        // 将holder，这个holder为开始在oncreat里面取得的holder，将它赋给surfaceHolder
        mSurfaceHolder = holder;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mCamera == null) {
            if (!initCamera()) {
                showFailDialog();
                return;
            }

        }
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
            handleSurfaceChanged();
        } catch (Exception e1) {
            LogUtils.e("video", "start preview fail " + e1.getMessage());
            showFailDialog();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        LogUtils.v("video", "surfaceDestroyed");
    }

    public boolean startRecording() {
        if (mediaRecorder == null) {
            if (!initRecorder())
                return false;
        }
        mediaRecorder.setOnInfoListener(this);
        mediaRecorder.setOnErrorListener(this);
        mediaRecorder.start();
        return true;
    }

    private boolean initRecorder() {
        if (!EaseCommonUtils.isExitsSdcard()) {
            showNoSDCardDialog();
            return false;
        }

        if (mCamera == null) {
            if (!initCamera()) {
                showFailDialog();
                return false;
            }
        }
        mVideoView.setVisibility(View.VISIBLE);
        // TODO init button
        mCamera.stopPreview();
        mediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        // 设置录制视频源为Camera（相机）
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        if (frontCamera == 1) {
            mediaRecorder.setOrientationHint(270);
        } else {
            mediaRecorder.setOrientationHint(90);
        }
        // 设置录制完成后视频的封装格式THREE_GPP为3gp.MPEG_4为mp4
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        // 设置录制的视频编码h263 h264
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错
        LogUtils.d("previewWidth = " + previewWidth + " previewHeight = " + previewHeight);
        mediaRecorder.setVideoSize(previewWidth, previewHeight);
        // 设置视频的比特率
//        1080＊720的分度辨bai率，用5000K左右；
//        720＊576的分辨率，用3500K左右；
//        640＊480的分辨率，用1500K左右。
        mediaRecorder.setVideoEncodingBitRate(5*1024*1024); //清晰
//        mediaRecorder.setVideoEncodingBitRate(900*1024);//较为清晰，且文件大小
//        为3.26M(30秒)
//        mediaRecorder.setVideoEncodingBitRate(384 * 1024);
        // // 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错
        if (defaultVideoFrameRate != -1) {
            //mediaRecorder.setVideoFrameRate(defaultVideoFrameRate);
        }
        // 设置视频文件输出的路径
        localPath = PathUtil.getInstance().getVideoPath()
                + System.currentTimeMillis() + ".mp4";
        LogUtils.d("文件路径是: " + localPath);
        mediaRecorder.setOutputFile(localPath);
        mediaRecorder.setMaxDuration(30000);
        mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }

    public void stopRecording() {
        if (mediaRecorder != null) {
            autoFocusManage.unregisterListener();
            mediaRecorder.setOnErrorListener(null);
            mediaRecorder.setOnInfoListener(null);
            try {
                mediaRecorder.stop();
            } catch (IllegalStateException e) {
                LogUtils.e("video", "stopRecording error:" + e.getMessage());
            }
        }
        releaseRecorder();

        if (mCamera != null) {
            mCamera.stopPreview();
            isPreviewActive = false;
            releaseCamera();
        }
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    protected void releaseCamera() {
        try {
            if (mCamera != null) {
                isPreviewActive = false;
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
        }
    }

    private float mLastX, mLastY, mLastZ;
    private boolean initFirstSensor = true;


    public void switchCamera() {

        if (mCamera == null) {
            return;
        }
        if (Camera.getNumberOfCameras() >= 2) {
            btn_switch.setEnabled(false);
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            switch (frontCamera) {
                case 0:
                    mCamera = Camera.open(CameraInfo.CAMERA_FACING_FRONT);
                    frontCamera = 1;
                    break;
                case 1:
                    mCamera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
                    frontCamera = 0;
                    break;
            }
            try {
                mCamera.lock();
                mCamera.setDisplayOrientation(90);
                mCamera.setPreviewDisplay(mVideoView.getHolder());
                mCamera.startPreview();
                isPreviewActive = true;
            } catch (IOException e) {
                mCamera.release();
                mCamera = null;
            }
            btn_switch.setEnabled(true);

        }

    }

    MediaScannerConnection msc = null;
    ProgressDialog progressDialog = null;

    public void sendVideo(View view) {
        if (TextUtils.isEmpty(localPath)) {
            LogUtils.e("Recorder", "recorder fail please try again!");
            return;
        }
        if (msc == null)
            msc = new MediaScannerConnection(this,
                    new MediaScannerConnectionClient() {

                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            LogUtils.d(TAG, "scanner completed");
                            msc.disconnect();
                            progressDialog.dismiss();
                            setResult(RESULT_OK, getIntent().putExtra("uri", uri));
                            finish();
                        }

                        @Override
                        public void onMediaScannerConnected() {
                            msc.scanFile(localPath, "video/*");
                        }
                    });


        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("processing...");
            progressDialog.setCancelable(false);
        }
        progressDialog.show();
        msc.connect();

    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        LogUtils.v("video", "onInfo");
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            LogUtils.v("video", "max duration reached");
            stopRecording();
            btn_switch.setVisibility(View.VISIBLE);
            chronometer.stop();
            btnStart.setVisibility(View.VISIBLE);
            btnStop.setVisibility(View.INVISIBLE);
            chronometer.stop();
            if (localPath == null) {
                return;
            }
            String st3 = getResources().getString(R.string.Whether_to_send);
            new AlertDialog.Builder(this)
                    .setMessage(st3)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface arg0,
                                                    int arg1) {
                                    arg0.dismiss();
                                    sendVideo(null);

                                }
                            }).setNegativeButton(R.string.cancel, null)
                    .setCancelable(false).show();
        }

    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        LogUtils.e("video", "recording onError:");
        stopRecording();
        Toast.makeText(this,
                "Recording error has occurred. Stopping the recording",
                Toast.LENGTH_SHORT).show();

    }

    public void saveBitmapFile(Bitmap bitmap) {
        File file = new File(Environment.getExternalStorageDirectory(), "a.jpg");
        try {
            BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseCamera();

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }

    }

    @Override
    public void onBackPressed() {
        back(null);
    }

    private void showFailDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.prompt)
                .setMessage(R.string.Open_the_equipment_failure)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                finish();

                            }
                        }).setCancelable(false).show();

    }

    private void showNoSDCardDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.prompt)
                .setMessage("No sd card!")
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                finish();

                            }
                        }).setCancelable(false).show();
    }

//    @Override
//    public void onSensorChanged(SensorEvent event) {
//        float x = event.values[0];
//        float y = event.values[1];
//        float z = event.values[2];
//        if (initFirstSensor) {//初始化默认进入时候的坐标
//            mLastX = x;
//            mLastY = y;
//            mLastZ = z;
//            initFirstSensor = false;
//            return;
//        }
//        float deltaX = Math.abs(mLastX - x);
//        float deltaY = Math.abs(mLastY - y);
//        float deltaZ = Math.abs(mLastZ - z);
//
//        LogUtils.d("deltaX = "+deltaX);
//        LogUtils.d("deltaY = "+deltaY);
//        LogUtils.d("deltaZ = "+deltaZ);
//        if (deltaX > 2.5 || deltaY > 2.5 || deltaZ > 2.5) {//计算坐标偏移值
//
//            //        获取最清晰拍摄焦距
//            initAutoFocus();
////            camera.autoFocus(new Camera.AutoFocusCallback() {
////                @Override
////                public void onAutoFocus(boolean success, Camera camera) {
////                    if (success) {
////                        //移动手机自动对焦,对焦成功实现自己的逻辑
////
////                    }
////                }
////            });
//        }
//        mLastX = x;
//        mLastY = y;
//        mLastZ = z;
//    }


}
