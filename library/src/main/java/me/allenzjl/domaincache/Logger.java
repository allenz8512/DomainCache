package me.allenzjl.domaincache;

import android.util.Log;

/**
 * The type Logger.
 */
public class Logger {

    private static final boolean DEBUG = true;

    private Logger() {
    }

    public static void d(String format, Object... args) {
        Log.d("DomainCache", String.format(format, args));
    }
}
