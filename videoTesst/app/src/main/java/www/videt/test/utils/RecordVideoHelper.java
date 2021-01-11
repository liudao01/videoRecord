package www.videt.test.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;

import java.util.Collection;

/**
 * CreateTime 2017/12/4 13:59
 * Author LiuShiHua
 * Description：打开和关闭闪光灯
 */

//        <uses-permission android:name="android.permission.FLASHLIGHT" />
//        <uses-permission android:name="android.permission.CAMERA"/>
//        <uses-feature android:name="android.hardware.camera" />
//        <uses-feature android:name="android.hardware.autofocus"/>

public class RecordVideoHelper {
    private static boolean isCanUse = false;
    private static boolean isOpenFrontCamera = false;

    //打开闪光灯
    public static boolean openFlashlight(Camera camera, Context context) {
        if (isCanUseLight(context, camera)) {
            isCanUse = true;
            doSetTorch(camera, true);
            return true;
        } else {
            Log.d("------------->", "手机不支持打开闪光灯");
            return false;
        }
    }

    //关闭闪光灯
    public static boolean closeFlashlight(Camera camera) {
        if (isCanUse) {
            doSetTorch(camera, false);
        }
        return isCanUse;
    }

    //是否可以使用闪光灯
    private static boolean isCanUseLight(Context context, Camera camera) {
        return camera != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    private static void doSetTorch(Camera camera, boolean newSetting) {
        Camera.Parameters parameters = camera.getParameters();
        String flashMode;
        /** 是否支持闪光灯 */
        if (newSetting) {
            flashMode = findSettableValue(parameters.getSupportedFlashModes(), Camera.Parameters.FLASH_MODE_TORCH, Camera.Parameters.FLASH_MODE_ON);
        } else {
            flashMode = findSettableValue(parameters.getSupportedFlashModes(), Camera.Parameters.FLASH_MODE_OFF);
        }
        if (flashMode != null) {
            parameters.setFlashMode(flashMode);
        }
        camera.setParameters(parameters);
    }

    private static String findSettableValue(Collection<String> supportedValues, String... desiredValues) {
        String result = null;
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        return result;
    }
}
