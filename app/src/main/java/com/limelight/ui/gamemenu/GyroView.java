package com.limelight.ui.gamemenu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.view.View;

public class GyroView extends View implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private Sensor accelSensor;

    private float[] gyroValues = new float[3];
    private float[] accelValues = new float[3];

    private Paint paint;
    private Paint textPaint;

    public GyroView(Context context) {
        super(context);
        init();
    }

    public GyroView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30);
        textPaint.setAntiAlias(true);
    }

    public void setSensorManager(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public void start() {
        if (sensorManager != null) {
            if (gyroSensor != null) {
                sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI);
            }
            if (accelSensor != null) {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    public void release() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroValues, 0, 3);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelValues, 0, 3);
        }
        invalidate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        // Draw background
        canvas.drawColor(Color.parseColor("#2C2C2C"));

        // Draw Axes
        paint.setColor(Color.GRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(0, centerY, width, centerY, paint);
        canvas.drawLine(centerX, 0, centerX, height, paint);

        // Draw Gyro Data (Visual representation)
        paint.setStrokeWidth(8);
        
        // X - Red
        paint.setColor(Color.RED);
        canvas.drawLine(centerX, centerY, centerX + gyroValues[0] * 100, centerY, paint);
        canvas.drawText("Gyro X: " + String.format("%.2f", gyroValues[0]), 20, 40, textPaint);

        // Y - Green
        paint.setColor(Color.GREEN);
        canvas.drawLine(centerX, centerY, centerX, centerY + gyroValues[1] * 100, paint);
        canvas.drawText("Gyro Y: " + String.format("%.2f", gyroValues[1]), 20, 80, textPaint);

        // Z - Blue
        paint.setColor(Color.BLUE);
        float zSize = gyroValues[2] * 50;
        canvas.drawCircle(centerX, centerY, Math.abs(zSize), paint);
        canvas.drawText("Gyro Z: " + String.format("%.2f", gyroValues[2]), 20, 120, textPaint);

        // Accel Data
        canvas.drawText("Accel X: " + String.format("%.2f", accelValues[0]), width - 250, 40, textPaint);
        canvas.drawText("Accel Y: " + String.format("%.2f", accelValues[1]), width - 250, 80, textPaint);
        canvas.drawText("Accel Z: " + String.format("%.2f", accelValues[2]), width - 250, 120, textPaint);
    }
}
