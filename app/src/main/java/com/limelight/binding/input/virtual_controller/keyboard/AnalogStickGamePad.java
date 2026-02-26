package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;

public class AnalogStickGamePad extends KeyAnalogStick {
    public AnalogStickGamePad(KeyBoardController controller, String elementId, Context context, boolean isLeft,boolean fixedStroke) {
        super(controller, context, elementId);
        setTextTipValues(new String[]{});
        addAnalogStickListener(new AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                float finalX = x;
                float finalY = y;
                //固定行程 圆形打满
                if(fixedStroke){
                    if (x != 0 || y != 0) {
                        // 计算当前触摸点到中心的距离
                        float mag = (float) Math.sqrt(x * x + y * y);
                        // 归一化：将长度强制拉伸到 1.0
                        finalX = x / mag;
                        finalY = y / mag;
                    }
                }
                if(isLeft){
                    inputContext.leftStickX = (short) (finalX * 0x7FFE);
                    inputContext.leftStickY = (short) (finalY * 0x7FFE);
                }else{
                    inputContext.rightStickX = (short) (finalX * 0x7FFE);
                    inputContext.rightStickY = (short) (finalY * 0x7FFE);
                }
                controller.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                if(PreferenceConfiguration.readPreferences(getContext()).disableRockerClickL3R3){
                    return;
                }
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= isLeft?ControllerPacket.LS_CLK_FLAG:ControllerPacket.RS_CLK_FLAG;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                if(PreferenceConfiguration.readPreferences(getContext()).disableRockerClickL3R3){
                    return;
                }
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~(isLeft?ControllerPacket.LS_CLK_FLAG:ControllerPacket.RS_CLK_FLAG);

                controller.sendControllerInputContext();
            }
        });
    }
}
