package com.limelight.binding.input.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;

import java.nio.ByteBuffer;

public class Dualshock4Controller extends AbstractDualSenseController {
   private static final int[] SUPPORTED_VENDORS = {
           0x054C//索尼
   };
   private static final int[] SUPPORTED_PRODUCTS = {
           0x05c4,//ps4一代
           0x09cc//ps4二代
   };

   public static boolean canClaimDevice(UsbDevice device) {
      for (int supportedVid : SUPPORTED_VENDORS) {
         for (int supportedPid:SUPPORTED_PRODUCTS) {
            if (device.getVendorId() == supportedVid
                    && device.getProductId()==supportedPid
                    && device.getInterfaceCount() >= 1) {
               return true;
            }
         }
      }
      return false;
   }

   public Dualshock4Controller(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
      super(device, connection, deviceId, listener);
   }

   private float normalizeThumbStickAxis(int value) {
      return (2.0f * value / 255.0f) - 1.0f;
   }

   private float normalizeTriggerAxis(int value) {
      return value / 255.0f;
   }

   @Override
   protected boolean handleRead(ByteBuffer buffer) {
      //https://www.psdevwiki.com/ps4/DS4-USB 参考
      if (buffer.remaining() != 64) {
         Log.d("Dualshock4Controller", "No Dualshock4Controller input: " + buffer.remaining());
         return false;
      }

      // Skip first byte
      buffer.get();

      // Process D-pad (buttons0 & 0x0F)
      int dpad = buffer.get(5) & 0x0F;

      setButtonFlag(ControllerPacket.UP_FLAG, (dpad == 0 || dpad == 1 || dpad == 7)?0x01:0);
      setButtonFlag(ControllerPacket.DOWN_FLAG, (dpad == 3 || dpad == 4 || dpad == 5)?0x02:0);
      setButtonFlag(ControllerPacket.LEFT_FLAG,  (dpad == 5 || dpad == 6 || dpad == 7)?0x04:0);
      setButtonFlag(ControllerPacket.RIGHT_FLAG, (dpad == 1 || dpad == 2 || dpad == 3)?0x08:0);

      //ABXY
      setButtonFlag(ControllerPacket.A_FLAG, buffer.get(5) & 0x20);
      setButtonFlag(ControllerPacket.B_FLAG, buffer.get(5) & 0x40);
      setButtonFlag(ControllerPacket.X_FLAG, buffer.get(5) & 0x10);
      setButtonFlag(ControllerPacket.Y_FLAG, buffer.get(5) & 0x80);

      // LB/RB
      setButtonFlag(ControllerPacket.LB_FLAG, buffer.get(6) & 0x01);
      setButtonFlag(ControllerPacket.RB_FLAG, buffer.get(6) & 0x02);

      // Start/Select
      setButtonFlag(ControllerPacket.BACK_FLAG, buffer.get(6) & 0x10);
      setButtonFlag(ControllerPacket.PLAY_FLAG, buffer.get(6) & 0x20);

      // LS/RS
      setButtonFlag(ControllerPacket.LS_CLK_FLAG, buffer.get(6) & 0x40);
      setButtonFlag(ControllerPacket.RS_CLK_FLAG, buffer.get(6) & 0x80);

      // PS button
      setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buffer.get(7) & 0x01);
//      setButtonFlag(ControllerPacket.MISC_FLAG, buffer.get(7) & 0x04); // Screenshot
      setButtonFlag(ControllerPacket.TOUCHPAD_FLAG, buffer.get(7) & 0x02);

      // Process analog sticks
      int axes0 = buffer.get(1) & 0xFF;
      int axes1 = buffer.get(2) & 0xFF;
      int axes2 = buffer.get(3) & 0xFF;
      int axes3 = buffer.get(4) & 0xFF;

      int axes4 = buffer.get(8) & 0xFF;
      int axes5 = buffer.get(9) & 0xFF;

      float lsx = normalizeThumbStickAxis(axes0);
      float lsy = normalizeThumbStickAxis(axes1);
      float rsx = normalizeThumbStickAxis(axes2);
      float rsy = normalizeThumbStickAxis(axes3);

      float l2axis = normalizeTriggerAxis(axes4);
      float r2axis = normalizeTriggerAxis(axes5);

      leftTrigger = l2axis;
      rightTrigger = r2axis;

      leftStickX = lsx;
      leftStickY = lsy;

      rightStickX = rsx;
      rightStickY = rsy;

//       IMU data
      final float GYRO_SCALE = 2000.0f / 32768.0f;
      final float ACCEL_SCALE = 4.0f / 32768.0f;
      final float G_TO_MS2 = 9.81f;

      // 读取 IMU 数据
      int gyrox = buffer.getShort(13);
      int gyroy = buffer.getShort(15);
      int gyroz = buffer.getShort(17);

      int accelx = buffer.getShort(19);
      int accely = buffer.getShort(21);
      int accelz = buffer.getShort(23);

      // 转换陀螺仪数据到 deg/s
      float gyroX_dps = gyrox * GYRO_SCALE;
      float gyroY_dps = gyroy * GYRO_SCALE;
      float gyroZ_dps = gyroz * GYRO_SCALE;

      // 转换加速度数据到 m/s²
      float accelX_ms2 = (accelx * ACCEL_SCALE) * G_TO_MS2;
      float accelY_ms2 = (accely * ACCEL_SCALE) * G_TO_MS2;
      float accelZ_ms2 = (accelz * ACCEL_SCALE) * G_TO_MS2;

      gyroX = gyroX_dps;
      gyroY = gyroY_dps;
      gyroZ = gyroZ_dps;

      accelX = accelX_ms2;
      accelY = accelY_ms2;
      accelZ = accelZ_ms2;
//      LimeLog.info("axi->accelx:"+accelX);
//      LimeLog.info("axi->accely:"+accelY);
//      LimeLog.info("axi->accelz:"+accelZ);
//
//      LimeLog.info("axi->gyroz:"+gyroZ);
//      LimeLog.info("axi->gyrox:"+gyroX);
//      LimeLog.info("axi->gyroy:"+gyroY);


      // Touch pad
//      int touch00 = buffer.get(33) & 0xFF;
//      int touch01 = buffer.get(34) & 0xFF;
//      int touch02 = buffer.get(35) & 0xFF;
//      int touch03 = buffer.get(36) & 0xFF;
//      int touch10 = buffer.get(37) & 0xFF;
//      int touch11 = buffer.get(38) & 0xFF;
//      int touch12 = buffer.get(39) & 0xFF;
//      int touch13 = buffer.get(40) & 0xFF;
//
//      boolean touch0active = (touch00 & 0x80) == 0;
//      int touch0id = touch00 & 0x7F;
//      int touch0x = ((touch02 & 0x0F) << 8) | touch01;
//      int touch0y = (touch03 << 4) | ((touch02 & 0xF0) >> 4);
//      if(touch0active){
////         LimeLog.info("axi->touch0active:"+touch0active);
////         LimeLog.info("axi->touch0id:"+touch0id);
////         LimeLog.info("axi->touch0x:"+touch0x);
////         LimeLog.info("axi->touch0y:"+touch0y);
////         t0x = normalizeTouchValue(touch0x, 0, 1920);
////         t0y = normalizeTouchValue(touch0y, 0, 1080);
//      }
//      boolean touch1active = (touch10 & 0x80) == 0;
//      int touch1id = touch10 & 0x7F;
//      int touch1x = ((touch12 & 0x0F) << 8) | touch11;
//      int touch1y = (touch13 << 4) | ((touch12 & 0xF0) >> 4);
//      if(touch1active){
//         LimeLog.info("axi->touch1active:"+touch1active);
//         LimeLog.info("axi->touch1id:"+touch1id);
//         LimeLog.info("axi->touch1x:"+touch1x);
//         LimeLog.info("axi->touch1y:"+touch1y);
//      }
      // Return true to send input
      return true;
   }

   private float normalizeTouchValue(float value, float min, float max) {
      return (value - min) / (max - min);
   }


   @Override
   protected boolean doInit() {
      Log.d("Dualshock4Controller", "doInit");
      sendCommand(getInitData());
      return true;
   }

   @Override
   public void rumble(short lowFreqMotor, short highFreqMotor) {
      //https://github.com/Ryochan7/DS4Windows/blob/master/DS4Windows/DS4Library/DS4Device.cs line:1561
      byte[] report=new byte[32];
      report[0] = 0x05;
      // Headphone volume L (0x10), Headphone volume R (0x20), Mic volume (0x40), Speaker volume (0x80)
      // enable rumble (0x01), lightbar (0x02), flash (0x04). Default: 0x07
      report[1] = (byte) 0x01;
      report[2] =0x04;

      report[4] = (byte)(highFreqMotor>> 8);// fast motor
      report[5] = (byte)(lowFreqMotor>> 8); // slow  motor
      report[6] = (byte) 0x78;  // red
      report[7] = (byte) 0x78;  // green
      report[8] = (byte) 0xEF;  // blue

      sendCommand(report);
   }

   @Override
   public void rumbleTriggers(short leftTrigger, short rightTrigger) {

   }

   @Override
   public void sendCommand(byte[] data) {
      Log.d("Dualshock4Controller", "sendCommand");
      int res = connection.bulkTransfer(outEndpt, data, data.length, 1000);
      Log.e("Dualshock4Controller", "Command transfer result: " + res);
      if (res != data.length) {
         Log.d("Dualshock4Controller", "Command set transfer failed: " + res);
      }
   }

   private byte[] getInitData(){
      byte[] report=new byte[32];
      report[0] = 0x05;
      // Headphone volume L (0x10), Headphone volume R (0x20), Mic volume (0x40), Speaker volume (0x80)
      // enable rumble (0x01), lightbar (0x02), flash (0x04). Default: 0x07
      report[1] = (byte) 0x02;
      report[2] =0x04;

      report[4] = 0x00;// fast motor
      report[5] = 0x00; // slow  motor
      report[6] = (byte) 0x78;  // red
      report[7] = (byte) 0x78;  // green
      report[8] = (byte) 0xEF;  // blue

      return report;
   }


}