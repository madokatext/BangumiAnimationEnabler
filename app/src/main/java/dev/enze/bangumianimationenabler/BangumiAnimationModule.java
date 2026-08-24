package dev.enze.bangumianimationenabler;

import android.animation.ValueAnimator;
import android.provider.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

/**
 * Presents a 0.5x animation environment to Bangumi while leaving the real system settings
 * untouched.
 */
public final class BangumiAnimationModule extends XposedModule {
    private static final String TAG = "BangumiAnimEnabler";
    private static final String TARGET_PACKAGE = "com.czy0729.bangumi";
    private static final float NORMAL_SCALE = 0.5f;

    private static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";
    private static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";
    private static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";

    /**
     * API 100 module entry point. LSPosed 1.11 instantiates entries through this exact constructor.
     */
    public BangumiAnimationModule(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);

        String processName = param.getProcessName();
        if (!TARGET_PACKAGE.equals(processName)
                && !processName.startsWith(TARGET_PACKAGE + ":")) {
            return;
        }

        installValueAnimatorHooks();
        installSettingsHooks();
        log(TAG + ": 0.5x animations enabled for " + processName);
    }

    private void installValueAnimatorHooks() {
        try {
            Method setDurationScale =
                    ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
            hook(setDurationScale, SetDurationScaleHooker.class);

            // The framework normally calls this setter after the module has loaded. Invoking it here
            // also fixes an already-initialized process and notifies any duration-scale listeners.
            try {
                setDurationScale.setAccessible(true);
                setDurationScale.invoke(null, NORMAL_SCALE);
            } catch (Throwable invokeError) {
                forceDurationScaleField();
                log(TAG + ": Could not invoke ValueAnimator.setDurationScale", invokeError);
            }
        } catch (Throwable error) {
            forceDurationScaleField();
            log(TAG + ": Could not hook ValueAnimator.setDurationScale", error);
        }

        try {
            Method getDurationScale = ValueAnimator.class.getDeclaredMethod("getDurationScale");
            hook(getDurationScale, GetDurationScaleHooker.class);
        } catch (Throwable error) {
            log(TAG + ": Could not hook ValueAnimator.getDurationScale", error);
        }

        try {
            Method areAnimatorsEnabled =
                    ValueAnimator.class.getDeclaredMethod("areAnimatorsEnabled");
            hook(areAnimatorsEnabled, AreAnimatorsEnabledHooker.class);
        } catch (Throwable error) {
            log(TAG + ": Could not hook ValueAnimator.areAnimatorsEnabled", error);
        }
    }

    private void forceDurationScaleField() {
        try {
            Field field = ValueAnimator.class.getDeclaredField("sDurationScale");
            field.setAccessible(true);
            field.setFloat(null, NORMAL_SCALE);
        } catch (Throwable error) {
            log(TAG + ": Could not set ValueAnimator.sDurationScale", error);
        }
    }

    private void installSettingsHooks() {
        for (Method method : Settings.Global.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length < 2 || parameterTypes[1] != String.class) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            boolean supported = returnType == String.class
                    || returnType == float.class
                    || returnType == Float.class
                    || returnType == int.class
                    || returnType == Integer.class;
            if (!method.getName().startsWith("get") || !supported) {
                continue;
            }

            try {
                hook(method, SettingsGlobalHooker.class);
            } catch (Throwable error) {
                log(TAG + ": Could not hook Settings.Global." + method.getName(), error);
            }
        }
    }

    @XposedHooker
    public static final class SetDurationScaleHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            Object[] args = callback.getArgs();
            if (args.length != 0) {
                args[0] = NORMAL_SCALE;
            }
        }
    }

    @XposedHooker
    public static final class GetDurationScaleHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            callback.returnAndSkip(NORMAL_SCALE);
        }
    }

    @XposedHooker
    public static final class AreAnimatorsEnabledHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            callback.returnAndSkip(true);
        }
    }

    @XposedHooker
    public static final class SettingsGlobalHooker implements XposedInterface.Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            Object[] args = callback.getArgs();
            if (args.length < 2 || !isAnimationScaleKey(args[1])) {
                return;
            }

            Member member = callback.getMember();
            if (!(member instanceof Method)) {
                return;
            }

            Class<?> returnType = ((Method) member).getReturnType();
            if (returnType == String.class) {
                callback.returnAndSkip("0.5");
            } else if (returnType == float.class || returnType == Float.class) {
                callback.returnAndSkip(NORMAL_SCALE);
            } else if (returnType == int.class || returnType == Integer.class) {
                callback.returnAndSkip(1);
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
