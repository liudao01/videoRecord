package www.videt.test.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;


/**
 * CreateTime 2017/10/16 15:00
 * Author LiuShiHua
 * Description：录制视频
 * <p>
 * 1.在Activity的oncreate方法中初始化
 * 2.在onstart中调用RecordVideoUtil的startPreview方法生成预览界面
 * 3.在onstop中调用RecordVideoUtil的stopRecord和stotPreview方法终止录制、预览界面
 * <p>
 * duration 默认录制时间
 */

public class RecordVideoUtil {
    /**
     * 默认录制时间是30秒
     */
    private int duration = 30;

    private SurfaceHolder mSurfaceHolder;
    private Context context;
    private boolean mIsSufaceCreated = false;
    private Camera mCamera;
    private final static int CAMERA_ID = 0;
    private boolean isRecording = false;

    private final String TAG = "------------>录像";
    private MediaRecorder mRecorder;
    private long start, end;
    private String savePath;

    private static int cameraPosition = 1;//0代表前置摄像头，1代表后置摄像头
    private boolean isView = true;
    private boolean highQuality = false;//是否录制高质量音频


    public RecordVideoUtil(Context context, SurfaceView mCameraPreview) {
        this.context = context;
        mSurfaceHolder = mCameraPreview.getHolder();
        mSurfaceHolder.addCallback(mSurfaceCallback);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mIsSufaceCreated = false;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mIsSufaceCreated = true;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            startPreview();
        }
    };

    //启动预览
    public boolean startPreview() {
        //保证只有一个Camera对象
        if (mCamera != null || !mIsSufaceCreated) {
            Log.d(TAG, "startPreview will return");
            return true;
        }
        try {
            mCamera = Camera.open(CAMERA_ID);
        } catch (Exception e) {//未授权相机权限，这里会抛出异常
            Toast.makeText(context, "打开相机失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return false;
        }
        Camera.Parameters parameters = mCamera.getParameters();
//        Camera.Size size = getBestPreviewSize(, CameraUtils.PREVIEW_HEIGHT, parameters);
//        if (size != null) {
//            parameters.setPreviewSize(size.width, size.height);
//        }
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        parameters.setPreviewFrameRate(20);
        //设置相机预览方向
        mCamera.setDisplayOrientation(90);
        mCamera.setParameters(parameters);

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        mCamera.startPreview();
        return true;
    }

    //打开闪光灯
    public boolean openLight() {
        if (cameraPosition == 0) {
            return false;
        }
        return RecordVideoHelper.openFlashlight(mCamera, context);
    }

    //关闭闪光灯
    public boolean closeLight() {
        return RecordVideoHelper.closeFlashlight(mCamera);
    }

    /**
     * 外部调用
     *
     * @param savePath 存储路径
     * @return 开始是否成功
     */
    public boolean startRecord(String savePath) {
        this.savePath = savePath;
        if (isRecording) {
            return false;
        }
        if (!hasSdcard()) {
            Toast.makeText(context, "请先插入SD卡(存储卡)", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!isSDCanUseSize50M()) {
            Toast.makeText(context, "内存已经不足50M了，请先清理手机空间", Toast.LENGTH_SHORT).show();
        }
        File file = new File(savePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, "文件创建失败", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        mRecorder = new MediaRecorder();//实例化
        mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());//预览
        mCamera.unlock();
        setMeadiaRecorder(mRecorder, file.getPath());
        try {
            mRecorder.prepare();
            mRecorder.start();
            isRecording = true;
            start = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
            if (mCamera != null) {
                mCamera.lock();
            }
            isRecording = false;
            mRecorder.release();
            return false;
        }
        return true;
    }

    /**
     * 停止录制
     *
     * @return
     */
    public boolean stopRecord() {
        end = System.currentTimeMillis();
        Log.d("---------->", "停止录制");
        if (!isRecording || mRecorder == null) {
            return false;
        }
        try {
            mRecorder.stop();
        } catch (Exception e) {
            mRecorder = null;
            mRecorder = new MediaRecorder();
        }
        mRecorder.release();
        mRecorder = null;
        if (mCamera != null) {
            mCamera.lock();
        }
        isRecording = false;
        //重启预览
        if (!startPreview())
            return false;
        if (((double) (end - start) / 1000) < 1) {
            if (savePath != null && new File(savePath).exists()) {
                new File(savePath).delete();
            }
        }
        return true;
    }

    /**
     * 在activity中的onPause中调用
     * <p>
     * 释放Camera对象
     */
    public void stopPreview() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewDisplay(null);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            isView = false;
        }
    }

    /**
     * 设置参数
     *
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
        if (cameraPosition == 1) {
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

    /**
     * 检查设备是否存在SDCard的工具方法
     */
    public static boolean hasSdcard() {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            // 有存储的SDCard
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获得sd卡剩余容量是否有50M，即可用大小
     *
     * @return
     */
    public static boolean isSDCanUseSize50M() {
        if (!hasSdcard()) {
            return false;
        }
        File path = Environment.getExternalStorageDirectory();
        StatFs sf = new StatFs(path.getPath());
        long size = sf.getBlockSize();//SD卡的单位大小
        long available = sf.getAvailableBlocks();//可使用的数量
        DecimalFormat df = new DecimalFormat();
        df.setGroupingSize(3);//每3位分为一组
        if (size * available / 1024 / 1024 < 50) {
            return false;
        }
        return true;
    }

    /**
     * 切换前置/后置摄像头
     */
    public void changeUseCamera() {
        if (mCamera == null || mSurfaceHolder == null) return;
        //切换前后摄像头
        if (!isCanChange) return;
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();//得到摄像头的个数

        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);//得到每一个摄像头的信息
            if (cameraPosition == 1) {
                //现在是后置，变更为前置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    stopPreview();
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    try {
                        mCamera.setPreviewDisplay(mSurfaceHolder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (mCamera != null && !isView) {
                        try {
                            mCamera.startPreview();//开始预览
                            mCamera.setDisplayOrientation(90);
                            Camera.Parameters parameters = mCamera.getParameters();
                            parameters.setPreviewFrameRate(5);
                            //设置旋转代码
                            parameters.setRotation(90);
                            cameraPosition = 0;
                            changeCameraTimer();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
            } else {
                //现在是前置， 变更为后置
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    stopPreview();
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    try {
                        mCamera.setPreviewDisplay(mSurfaceHolder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (mCamera != null && !isView) {
                        try {
                            mCamera.startPreview();//开始预览
                            mCamera.setDisplayOrientation(90);
                            Camera.Parameters parameters = mCamera.getParameters();
                            parameters.setPreviewFrameRate(5);
                            //设置旋转代码
                            parameters.setRotation(90);
                            mCamera.setParameters(parameters);
                            cameraPosition = 1;
                            changeCameraTimer();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
            }

        }
    }

    private boolean isCanChange = true;

    private void changeCameraTimer() {
        isCanChange = false;
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                isCanChange = true;
            }
        }, 600);
    }

    /**
     * 获取本地视频的第一帧
     *
     * @param filePath
     * @return
     */
    public static Bitmap getVideoFirstFrame(String filePath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();//实例化MediaMetadataRetriever对象
        File file = new File(filePath);//实例化File对象，文件路径为/storage/sdcard/Movies/music1.mp4
        if (file.exists()) {
            mmr.setDataSource(file.getAbsolutePath());//设置数据源为该文件对象指定的绝对路径
            Bitmap bitmap = mmr.getFrameAtTime();//获得视频第一帧的Bitmap对象
            return bitmap;
        }
        return null;
    }

    /**
     * 获取网络视频的第一帧
     *
     * @param videoUrl
     * @return
     */
    public static Bitmap getVideoFirstFrame_Net(String videoUrl) {
        Bitmap bitmap = null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            //根据url获取缩略图
            retriever.setDataSource(videoUrl, new HashMap());
            //获得第一帧图片
            bitmap = retriever.getFrameAtTime();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            retriever.release();
        }
        return bitmap;
    }

}
