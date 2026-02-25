/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;


public class TriggerGamePad extends KeyBoardDigitalButton {

    public TriggerGamePad(KeyBoardController controller, String elementId, String name,boolean isLeft,
                          boolean switchMode,int layer, Context context) {
        super(controller, elementId, layer, context);
        setText(name);
        setEnableSwitchDown(switchMode);
        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                if(isLeft){
                    inputContext.leftTrigger = (byte) 0xFF;
                }else{
                    inputContext.rightTrigger = (byte) 0xFF;
                }
                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
                KeyBoardController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                if(isLeft){
                    inputContext.leftTrigger = (byte) 0x00;
                }else{
                    inputContext.rightTrigger = (byte) 0x00;
                }
                controller.sendControllerInputContext();
            }
        });
    }
}
