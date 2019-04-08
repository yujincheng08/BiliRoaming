package me.iacn.biliroaming;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;

import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
public class BiliBiliPackage {

    private static volatile BiliBiliPackage sInstance;
    private ClassLoader mClassLoader;

    private WeakReference<Class<?>> bangumiApiResponseClass;
    private WeakReference<Class<?>> fastJsonClass;
    private WeakReference<Class<?>> bangumiUniformSeasonClass;

    private BiliBiliPackage() {
    }

    public static BiliBiliPackage getInstance() {
        if (sInstance == null) {
            synchronized (BiliBiliPackage.class) {
                if (sInstance == null) {
                    sInstance = new BiliBiliPackage();
                }
            }
        }
        return sInstance;
    }

    public void init(ClassLoader classLoader) {
        this.mClassLoader = classLoader;
    }

    public Class<?> bangumiApiResponse() {
        bangumiApiResponseClass = checkNullOrReturn(bangumiApiResponseClass, "com.bilibili.bangumi.api.BangumiApiResponse");
        return bangumiApiResponseClass.get();
    }

    public Class<?> fastJson() {
        if (fastJsonClass == null || fastJsonClass.get() == null) {
            Class<?> clazz;
            try {
                clazz = findClass("com.alibaba.fastjson.JSON", mClassLoader);
            } catch (ClassNotFoundError e) {
                clazz = findClass("com.alibaba.fastjson.a", mClassLoader);
            }
            fastJsonClass = new WeakReference<>(clazz);
        }

        return fastJsonClass.get();
    }

    public Class<?> bangumiUniformSeason() {
        bangumiUniformSeasonClass = checkNullOrReturn(bangumiUniformSeasonClass, "com.bilibili.bangumi.api.uniform.BangumiUniformSeason");
        return bangumiUniformSeasonClass.get();
    }

    private WeakReference<Class<?>> checkNullOrReturn(WeakReference<Class<?>> clazz, String className) {
        if (clazz == null || clazz.get() == null) {
            clazz = new WeakReference<>(findClass(className, mClassLoader));
        }
        return clazz;
    }
}