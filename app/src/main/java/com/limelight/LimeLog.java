package com.limelight;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class LimeLog {
    private static final Logger LOGGER = Logger.getLogger(LimeLog.class.getName());
    // 控制是否在 Release 版本打印到 Logcat
    private static final boolean IS_LOGGABLE = BuildConfig.DEBUG;

    public static void info(String msg) {
        if(IS_LOGGABLE){
            LOGGER.info(msg);
        }
    }
    
    public static void warning(String msg) {
        if(IS_LOGGABLE){
            LOGGER.warning(msg);
        }
    }
    
    public static void severe(String msg) {
        if(IS_LOGGABLE){
            LOGGER.severe(msg);
        }
    }
    
    public static void setFileHandler(String fileName) throws IOException {
        LOGGER.addHandler(new FileHandler(fileName));
    }
}
