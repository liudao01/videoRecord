package www.videt.test.utils;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Created by liuml on 1/11/21 10:02
 */
public class AutoFocusManage {

    private SensorManager sensorManager;
    private Camera camera;
    private float[] accelerometerValues;
    private float[] magneticFieldValues;
    private float[] orientationValues = new float[3];
    private SensorEventListener accelerometerSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            accelerometerValues = event.values;
            if (magneticFieldValues != null) {
                calculateOrientation();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private SensorEventListener magneticFieldSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            magneticFieldValues = event.values;
            if (accelerometerValues != null) {
                calculateOrientation();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public AutoFocusManage(Context context, Camera camera) {
        this.camera = camera;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        // 初始化加速度传感器
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // 初始化地磁场传感器
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(accelerometerSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(magneticFieldSensorEventListener, magneticField, SensorManager.SENSOR_DELAY_UI);
    }

    public void unregisterListener() {
        sensorManager.unregisterListener(accelerometerSensorEventListener);
        sensorManager.unregisterListener(magneticFieldSensorEventListener);
    }

    /**
     * 根据加速度和地磁场计算方向
     */
    private synchronized void calculateOrientation() {
        float[] values = new float[3];
        float[] R = new float[9];
        SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(R, values);
        float[] orientation = new float[3];
        orientation[0] = (float) Math.toDegrees(values[0]);
        orientation[1] = (float) Math.toDegrees(values[1]);
        orientation[2] = (float) Math.toDegrees(values[2]);
        LogUtils.d("根据加速度和地磁场计算方向 ");
        if (Math.abs(orientation[0] - orientationValues[0]) >= 8 || Math.abs(orientation[1] - orientationValues[1]) >= 8 || Math.abs(orientation[2] - orientationValues[2]) >= 8) {
            orientationValues = orientation;
            LogUtils.d("根据加速度和地磁场计算方向 orientationValues = " + orientationValues);
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    camera.cancelAutoFocus();
                }
            });
        }
    }
}
