package com.android.qshtclauncher2;

import com.mediatek.xlog.Xlog;

public final class LauncherLog {
    static final boolean DEBUG = false;
    static final boolean DEBUG_DRAW = false;
    static final boolean DEBUG_DRAG = false;
    static final boolean DEBUG_KEY = false;
    static final boolean DEBUG_LAYOUT = false;
    static final boolean DEBUG_LOADER = false;
    static final boolean DEBUG_MOTION = false;
    static final boolean DEBUG_PERFORMANCE = false;
    static final boolean DEBUG_SURFACEWIDGET = false;
    static final boolean DEBUG_UNREAD = false;
    public static final boolean DEBUG_AUTOTESTCASE = false;
    
    public static final boolean DEBUG_QS_I9300 = false;
    public static final boolean DEBUG_QS_HTC = false;

    private static final String MODULE_NAME = "QsLauncher";
    private static final LauncherLog INSTANCE = new LauncherLog();
    
    public static final boolean QS_STYLE_HTC = true;
    public static final boolean QS_STYLE_I9300 = false;

    /**
     * private constructor here, It is a singleton class.
     */
    private LauncherLog() {
    }

    /**
     * The FileManagerLog is a singleton class, this static method can be used
     * to obtain the unique instance of this class.
     * 
     * @return The global unique instance of FileManagerLog.
     */
    public static LauncherLog getInstance() {
        return INSTANCE;
    }

    /**
     * The method prints the log, level error.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void e(String tag, String msg) {
        Xlog.e(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level error.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void e(String tag, String msg, Throwable t) {
        Xlog.e(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level warning.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void w(String tag, String msg) {
        Xlog.w(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level warning.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void w(String tag, String msg, Throwable t) {
        Xlog.w(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void i(String tag, String msg) {
        Xlog.i(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t an exception to log.
     */
    public static void i(String tag, String msg, Throwable t) {
        Xlog.i(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void d(String tag, String msg) {
        Xlog.d(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t An exception to log.
     */
    public static void d(String tag, String msg, Throwable t) {
        Xlog.d(MODULE_NAME, tag + ", " + msg, t);
    }

    /**
     * The method prints the log, level debug.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     */
    public static void v(String tag, String msg) {
        Xlog.v(MODULE_NAME, tag + ", " + msg);
    }

    /**
     * The method prints the log, level debug.
     * 
     * @param tag the tag of the class.
     * @param msg the message to print.
     * @param t An exception to log.
     */
    public static void v(String tag, String msg, Throwable t) {
        Xlog.v(MODULE_NAME, tag + ", " + msg, t);
    }
}
