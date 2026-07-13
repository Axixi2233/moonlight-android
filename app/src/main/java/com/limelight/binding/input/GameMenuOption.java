package com.limelight.binding.input;

public final class GameMenuOption {
    public final String label;
    public final boolean withGameFocus;
    public final Runnable runnable;

    public GameMenuOption(String label, boolean withGameFocus, Runnable runnable) {
        this.label = label;
        this.withGameFocus = withGameFocus;
        this.runnable = runnable;
    }

    public GameMenuOption(String label, Runnable runnable) {
        this(label, false, runnable);
    }
}
