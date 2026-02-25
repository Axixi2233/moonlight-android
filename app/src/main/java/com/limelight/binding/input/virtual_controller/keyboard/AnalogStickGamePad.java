package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;

import com.limelight.nvstream.input.ControllerPacket;

public class AnalogStickGamePad extends KeyAnalogStick {
    public AnalogStickGamePad(KeyBoardController controller, String elementId, Context context, boolean isLeft) {
        super(controller, context, elementId);
        setTextTipValues(new String[]{});
        addAnalogStickListener(new AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                if(isLeft){
                    inputContext.leftStickX = (short) (x * 0x7FFE);
                    inputContext.leftStickY = (short) (y * 0x7FFE);
                }else{
                    inputContext.rightStickX = (short) (x * 0x7FFE);
                    inputContext.rightStickY = (short) (y * 0x7FFE);
                }
                controller.sendControllerInputContext();
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= isLeft?ControllerPacket.LS_CLK_FLAG:ControllerPacket.RS_CLK_FLAG;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRevoke() {
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~(isLeft?ControllerPacket.LS_CLK_FLAG:ControllerPacket.RS_CLK_FLAG);

                controller.sendControllerInputContext();
            }
        });
    }
}
