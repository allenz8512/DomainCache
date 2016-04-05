package me.allenzjl.domaincache;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;

/**
 * 获取应用的ApplicationContext
 */
public class ContextAccessor {

    private static Context appContext;

    public static synchronized Context getApplicationContext() {
        if (appContext == null) {
            try {
                final Class<?> activityThreadClass =
                        ContextAccessor.class.getClassLoader().loadClass("android.app.ActivityThread");
                final Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
                final Object activityThread = currentActivityThread.invoke(null);
                final Method getApplication = activityThreadClass.getDeclaredMethod("getApplication");
                final Application application = (Application) getApplication.invoke(activityThread);
                appContext = application.getApplicationContext();
            } catch (final Exception ignored) {
            }
        }
        return appContext;
    }
}
