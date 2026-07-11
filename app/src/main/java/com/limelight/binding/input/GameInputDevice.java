package com.limelight.binding.input;

import java.util.List;

/**
 * Description
 * Date: 2024-01-16
 * Time: 15:26
 */
public interface GameInputDevice {

    /**
     * @return list of device specific game menu options, e.g. configure a controller's mouse mode
     */
    List<GameMenuOption> getGameMenuOptions();
}
