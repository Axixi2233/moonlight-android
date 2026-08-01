package com.limelight.ui;

public interface GameGestures {
    void toggleKeyboard();

    default void showGameMenu() {}

    default void dispatchGameMenuKeyEvent(int keyCode, boolean down) {}
}
