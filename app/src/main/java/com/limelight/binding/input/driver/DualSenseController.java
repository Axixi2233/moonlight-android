package com.limelight.binding.input.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.limelight.nvstream.input.ControllerPacket;

import java.nio.ByteBuffer;

public class DualSenseController extends AbstractDualSenseController {
   private static final int[] SUPPORTED_VENDORS = {
           0x054C,//索尼
           0x1532//雷蛇
   };
   private static final int[] SUPPORTED_PRODUCTS = {
           0x0CE6,//ps5
           0x0DF2,//ps5 edge
           0x100b,//有线模式 雷蛇幻影战狼v2pro
           0x100c//无线模式 雷蛇幻影战狼v2pro
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

   public DualSenseController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
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
      if (buffer.remaining() != 64) {
         Log.d("DualController", "No Daulsense input: " + buffer.remaining());
         return false;
      }

      // Skip first byte
      buffer.get();

      // Process D-pad (buttons0 & 0x0F)
      int dpad = buffer.get(8) & 0x0F;

      setButtonFlag(ControllerPacket.UP_FLAG, (dpad == 0 || dpad == 1 || dpad == 7)?0x01:0);
      setButtonFlag(ControllerPacket.DOWN_FLAG, (dpad == 3 || dpad == 4 || dpad == 5)?0x02:0);
      setButtonFlag(ControllerPacket.LEFT_FLAG,  (dpad == 5 || dpad == 6 || dpad == 7)?0x04:0);
      setButtonFlag(ControllerPacket.RIGHT_FLAG, (dpad == 1 || dpad == 2 || dpad == 3)?0x08:0);

      //ABXY
      setButtonFlag(ControllerPacket.A_FLAG, buffer.get(8) & 0x20);
      setButtonFlag(ControllerPacket.B_FLAG, buffer.get(8) & 0x40);
      setButtonFlag(ControllerPacket.X_FLAG, buffer.get(8) & 0x10);
      setButtonFlag(ControllerPacket.Y_FLAG, buffer.get(8) & 0x80);

      // LB/RB
      setButtonFlag(ControllerPacket.LB_FLAG, buffer.get(9) & 0x01);
      setButtonFlag(ControllerPacket.RB_FLAG, buffer.get(9) & 0x02);

      // Start/Select
      setButtonFlag(ControllerPacket.BACK_FLAG, buffer.get(9) & 0x10);
      setButtonFlag(ControllerPacket.PLAY_FLAG, buffer.get(9) & 0x20);

      // LS/RS
      setButtonFlag(ControllerPacket.LS_CLK_FLAG, buffer.get(9) & 0x40);
      setButtonFlag(ControllerPacket.RS_CLK_FLAG, buffer.get(9) & 0x80);

      // PS button
      setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buffer.get(10) & 0x01);
      setButtonFlag(ControllerPacket.MISC_FLAG, buffer.get(10) & 0x04); // Screenshot
      setButtonFlag(ControllerPacket.TOUCHPAD_FLAG, buffer.get(10) & 0x02);

      // Process analog sticks
      int axes0 = buffer.get(1) & 0xFF;
      int axes1 = buffer.get(2) & 0xFF;
      int axes2 = buffer.get(3) & 0xFF;
      int axes3 = buffer.get(4) & 0xFF;
      int axes4 = buffer.get(5) & 0xFF;
      int axes5 = buffer.get(6) & 0xFF;

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
      int gyrox = buffer.getShort(16);
      int gyroy = buffer.getShort(18);
      int gyroz = buffer.getShort(20);

      int accelx = buffer.getShort(22);
      int accely = buffer.getShort(24);
      int accelz = buffer.getShort(26);

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
      Log.d("DualController", "doInit");
      sendCommand(getDualSenseInit());
      return true;
   }

   //参考https://gist.github.com/stealth-alex/10a8e7cc6027b78fa18a7f48a0d3d1e4
   //https://github.com/flok/pydualsense/blob/master/pydualsense/pydualsense.py
//   outputReport[1]  = 0xff; // flags determiing what changes this packet will perform
//   // 0x01 set the main motors (also requires flag 0x02); setting this by itself will allow rumble to gracefully terminate and then re-enable audio haptics, whereas not setting it will kill the rumble instantly and re-enable audio haptics.
//   // 0x02 set the main motors (also requires flag 0x01; without bit 0x01 motors are allowed to time out without re-enabling audio haptics)
//   // 0x04 set the right trigger motor
//   // 0x08 set the left trigger motor
//   // 0x10 modification of audio volume
//   // 0x20 toggling of internal speaker while headset is connected
//   // 0x40 modification of microphone volume
//   // 0x80 toggling of internal mic or external speaker while headset is connected

//    # further flags determining what changes this packet will perform
//            # 0x01 toggling microphone LED
//            # 0x02 toggling audio/mic mute
//            # 0x04 toggling LED strips on the sides of the touchpad
//            # 0x08 will actively turn all LEDs off? Convenience flag? (if so, third parties might not support it properly)
//           # 0x10 toggling white player indicator LEDs below touchpad
//            # 0x20 ???
//                    # 0x40 adjustment of overall motor/effect power (index 37 - read note on triggers)
//            # 0x80 ???

   @Override
   public void rumble(short lowFreqMotor, short highFreqMotor) {
      byte[] reportData = new byte[] {
              0x02, // Report ID
              (byte)(0x01|0x02), // valid_flag0
              (byte)0x00, // valid_flag1
              (byte)(highFreqMotor>> 8), // right trigger rumble
              (byte)(lowFreqMotor >> 8), // left trigger rumble
              0x00, 0x00, 0x00, 0x00,
              0x00,  // mute_button_led (0: mute LED off  | 1: mute LED on)
              0x10, // power_save_control(mute led on  = 0x00, off = 0x10)
              0x00,          // R2 trigger effect mode
              0x00, // R2 trigger effect parameter 1
              0x00, // R2 trigger effect parameter 2
              0x00, // R2 trigger effect parameter 3
              0x00,       // R2 trigger effect parameter 4
              0x00,       // R2 trigger effect parameter 5
              0x00,       // R2 trigger effect parameter 6
              0x00,       // R2 trigger effect parameter 7
              0x00, 0x00, 0x00,
              0x00,       // L2 trigger effect mode
              0x00,       // L2 trigger effect parameter 1
              0x00, // L2 trigger effect parameter 2
              0x00,       // L2 trigger effect parameter 3
              0x00,       // L2 trigger effect parameter 4
              0x00,       // L2 trigger effect parameter 5
              0x00,       // L2 trigger effect parameter 6
              0x00,       // L2 trigger effect parameter 7
              0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
              0x02, 0x00, 0x02, 0x00,
              0x00,       // player leds
              (byte) 0x78, (byte) 0x78, (byte) 0xEF // RGB values
      };
      sendCommand(reportData);
   }

   @Override
   public void rumbleTriggers(short leftTrigger, short rightTrigger) {

   }

   @Override
   public void sendCommand(byte[] data) {
      Log.d("DualController", "sendCommand");
      int res = connection.bulkTransfer(outEndpt, data, data.length, 1000);
      Log.e("DualController", "Command transfer result: " + res);
      if (res != data.length) {
         Log.d("DualController", "Command set transfer failed: " + res);
      }
   }

   private byte[] getDualSenseInit(){
      return new byte[] {
              0x02, // Report ID
              (byte)(0x10 | 0x20 | 0x40 | 0x80), // valid_flag0
              (byte)0xf7, // valid_flag1
              0x00, // right trigger rumble
              0x00, // left trigger rumble
              0x00, 0x00, 0x00, 0x00,
              0x00,  // mute_button_led (0: mute LED off  | 1: mute LED on)
              0x10, // power_save_control(mute led on  = 0x00, off = 0x10)
              0x00,          // R2 trigger effect mode 自动步枪
              0x00, // R2 trigger effect parameter 1 频率10
              0x00, // R2 trigger effect parameter 2 强度255
              0x00, // R2 trigger effect parameter 3 起始位置20
              0x00,       // R2 trigger effect parameter 4
              0x00,       // R2 trigger effect parameter 5
              0x00,       // R2 trigger effect parameter 6
              0x00,       // R2 trigger effect parameter 7
              0x00, 0x00, 0x00,
              0x00,       // L2 trigger effect mode 阻尼
              0x00,       // L2 trigger effect parameter 1 起始位置40
              0x00, // L2 trigger effect parameter 2 强度230
              0x00,       // L2 trigger effect parameter 3
              0x00,       // L2 trigger effect parameter 4
              0x00,       // L2 trigger effect parameter 5
              0x00,       // L2 trigger effect parameter 6
              0x00,       // L2 trigger effect parameter 7
              0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
              0x02, 0x00, 0x02, 0x00,
              0x00,       // player leds
              (byte) 0x78, (byte) 0x78, (byte) 0xEF // RGB values
      };
   }


   //自适应扳机报文
   public static byte[] getTriggerEffectMode(byte[] rM,byte[] lM){
      return new byte[] {
              0x02, // Report ID
              (byte)(0x04|0x08), // valid_flag0
              (byte)0xf7, // valid_flag1
              0x00, // right trigger rumble
              0x00, // left trigger rumble
              0x00, 0x00, 0x00, 0x00,
              0x00,  // mute_button_led (0: mute LED off  | 1: mute LED on)
              0x10, // power_save_control(mute led on  = 0x00, off = 0x10)
              rM[0],          // R2 trigger effect mode 自动步枪
              rM[1], // R2 trigger effect parameter 1 频率10
              rM[2], // R2 trigger effect parameter 2 强度255
              rM[3], // R2 trigger effect parameter 3 起始位置20
              0x00,       // R2 trigger effect parameter 4
              0x00,       // R2 trigger effect parameter 5
              0x00,       // R2 trigger effect parameter 6
              0x00,       // R2 trigger effect parameter 7
              0x00, 0x00, 0x00,
              lM[0],       // L2 trigger effect mode 阻尼
              lM[1],       // L2 trigger effect parameter 1 起始位置40
              lM[2], // L2 trigger effect parameter 2 强度230
              lM[3],       // L2 trigger effect parameter 3
              0x00,       // L2 trigger effect parameter 4
              0x00,       // L2 trigger effect parameter 5
              0x00,       // L2 trigger effect parameter 6
              0x00,       // L2 trigger effect parameter 7
              0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
              0x02, 0x00, 0x02, 0x00,
              0x00,       // player leds
              (byte) 0x78, (byte) 0x78, (byte) 0xEF // RGB values
      };
   }

   /**
    * 设置自适应扳机
    * @param mode 模式 0关闭 1阻尼 2扳机 6自动步枪
    * @param strength 震动强度
    * @param frequency 震动频率（mode=6生效）
    * @param start 起始位置
    * @param end 结束位置
    * @return
    */
   public static byte[] setTrigger(int mode,int strength,int frequency,int start,int end){
      if(mode==1){
         return new byte[] {(byte)0x01,(byte)(start&0xFF),(byte)(strength&0xFF),(byte)0x00};
      }
      if(mode==6){
         return new byte[] {(byte)0x06,(byte)(frequency&0xFF),(byte)(strength&0xFF),(byte)(start&0xFF)};
      }
      if(mode==2){
         return new byte[] {(byte)0x02,(byte)(start&0xFF),(byte)(end&0xFF),(byte)(strength&0xFF)};
      }
      return new byte[] {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};
   }

   //自动步枪
   public static byte[] automaticTrigger(){
//              0x06,          // R2 trigger effect mode 自动步枪
//              (byte)0x0a, // R2 trigger effect parameter 1 频率10
//              (byte)0xff, // R2 trigger effect parameter 2 强度255
//              (byte)0x14, // R2 trigger effect parameter 3 起始位置20
      return new byte[] {(byte)0x06,(byte)(10&0xFF),(byte)0xFF,(byte)20&0xFF};
   }

   //阻尼
   public static byte[] resistanceTrigger(){
//              0x01,       // L2 trigger effect mode 阻尼
//              0x28,       // L2 trigger effect parameter 1 起始位置40
//              (byte)0xE6, // L2 trigger effect parameter 2 强度230
//              0x00,       // L2 trigger effect parameter 3
      return new byte[] {(byte)0x01,(byte)0x28,(byte)0xE6,(byte)0x00};
   }

   //扳机
   public static byte[] normalTrigger(){
//              0x02,       // L2 trigger effect mode 扳机
//              0xF,       // L2 trigger effect parameter 1 起始位置40
//              (byte)0x64, // L2 trigger effect parameter 2 结束位置 100
//              0xff,       // L2 trigger effect parameter 3 强度255
      return new byte[] {(byte)0x02,(byte)0xF,(byte)0x64,(byte)0xff};
   }

   //关闭
   public static byte[] clearTrigger(){
//              0x02,       // L2 trigger effect mode
//              0xF,       // L2 trigger effect parameter 1
//              (byte)0x64, // L2 trigger effect parameter 2
//              0xff,       // L2 trigger effect parameter 3
      return new byte[] {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};
   }

}