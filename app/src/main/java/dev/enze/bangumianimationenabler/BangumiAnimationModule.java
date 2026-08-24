package dev.enze.bangumianimationenabler;

import android.animation.ValueAnimator;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

/**
 * Presents a normal 1x animation environment to Bangumi while leaving the real system settings
 * untouched.
 */
public final class BangumiAnimationModule extends XposedModule {
    private static final String TAG = "BangumiAnimEnabler";
    private static final String TARGET_PACKAGE = "com.czy0729.bangumi";
    private static final float NORMAL_SCALE = 1.0f;

    private static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";
    private static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";
    private static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        String processName = param.getProcessName();
        if (!TARGET_PACKAGE.equals(processName)
                && !processName.startsWith(TARGET_PACKAGE + ":")) {
            return;
        }

        installValueAnimatorHooks();
        installSettingsHooks();
        log(Log.INFO, TAG, "1x animations enabled for " + processName);
    }

    private void installValueAnimatorHooks() {
        try {
            Method setDurationScale =
                    ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
            hook(setDurationScale)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> chain.proceed(new Object[]{NORMAL_SCALE}));

            // The framework normally calls this setter after the module has loaded. Invoking it here
            // also fixes an already-initialized process and notifies any duration-scale listeners.
            try {
                setDurationScale.setAccessible(true);
                setDurationScale.invoke(null, NORMAL_SCALE);
            } catch (Throwable invokeError) {
                forceDurationScaleField();
                log(Log.WARN, TAG, "Could not invoke ValueAnimator.setDurationScale", invokeError);
            }
        } catch (Throwable error) {
            forceDurationScaleField();
            log(Log.ERROR, TAG, "Could not hook ValueAnimator.setDurationScale", error);
        }

        try {
            Method getDurationScale = ValueAnimator.class.getDeclaredMethod("getDurationScale");
            hook(getDurationScale)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> NORMAL_SCALE);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not hook ValueAnimator.getDurationScale", error);
        }

        try {
            Method areAnimatorsEnabled =
                    ValueAnimator.class.getDeclaredMethod("areAnimatorsEnabled");
            hook(areAnimatorsEnabled)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> true);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not hook ValueAnimator.areAnimatorsEnabled", error);
        }
    }

    private void forceDurationScaleField() {
        try {
            Field field = ValueAnimator.class.getDeclaredField("sDurationScale");
            field.setAccessible(true);
            field.setFloat(null, NORMAL_SCALE);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Could not set ValueAnimator.sDurationScale", error);
        }
    }

    private void installSettingsHooks() {
        for (Method method : Settings.Global.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length < 2 || parameterTypes[1] != String.class) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            Object replacement;
            if (returnType == String.class && method.getName().startsWith("getString")) {
                replacement = "1";
            } else if ((returnType == float.class || returnType == Float.class)
                    && method.getName().startsWith("getFloat")) {
                replacement = NORMAL_SCALE;
            } else if ((returnType == int.class || returnType == Integer.class)
                    && method.getName().startsWith("getInt")) {
                replacement = 1;
            } else {
                continue;
            }

            try {
                hook(method)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(chain -> isAnimationScaleKey(chain.getArg(1))
                                ? replacement
                                : chain.proceed());
            } catch (Throwable error) {
                log(Log.WARN, TAG, "Could not hook Settings.Global." + method.getName(), error);
            }
        }
    }

    private static boolean isAnimationScaleKey(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String key = (String) value;
        return WINDOW_ANIMATION_SCALE.equals(key)
                || TRANSITION_ANIMATION_SCALE.equals(key)
                || ANIMATOR_DURATION_SCALE.equals(key);
    }
}
