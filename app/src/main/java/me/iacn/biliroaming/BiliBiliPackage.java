package me.iacn.biliroaming;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
public class BiliBiliPackage {

    private static volatile BiliBiliPackage sInstance;

    private ClassLoader mClassLoader;
    private Map<String, String> mHookInfo;

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

    void init(ClassLoader classLoader, Context context) {
        this.mClassLoader = classLoader;

        readHookInfo(context);
        if (checkHookInfo()) {
            writeHookInfo(context);
        }
    }

    public String retrofitResponse() {
        return mHookInfo.get("class_retrofit_response");
    }

    public String fastJsonParse() {
        return mHookInfo.get("method_fastjson_parse");
    }

    public Class<?> bangumiApiResponse() {
        bangumiApiResponseClass = checkNullOrReturn(bangumiApiResponseClass, "com.bilibili.bangumi.api.BangumiApiResponse");
        return bangumiApiResponseClass.get();
    }

    public Class<?> bangumiUniformSeason() {
        bangumiUniformSeasonClass = checkNullOrReturn(bangumiUniformSeasonClass, "com.bilibili.bangumi.api.uniform.BangumiUniformSeason");
        return bangumiUniformSeasonClass.get();
    }

    public Class<?> fastJson() {
        fastJsonClass = checkNullOrReturn(fastJsonClass, mHookInfo.get("class_fastjson"));
        return fastJsonClass.get();
    }

    private WeakReference<Class<?>> checkNullOrReturn(WeakReference<Class<?>> clazz, String className) {
        if (clazz == null || clazz.get() == null) {
            clazz = new WeakReference<>(findClass(className, mClassLoader));
        }
        return clazz;
    }

    private void readHookInfo(Context context) {
        try {
            File hookInfoFile = new File(context.getCacheDir(), Constant.HOOK_INFO_FILE_NAME);
            Log.d(TAG, "Reading hook info: " + hookInfoFile);
            long startTime = System.currentTimeMillis();

            if (hookInfoFile.isFile() && hookInfoFile.canRead()) {
                long lastUpdateTime = context.getPackageManager().getPackageInfo(Constant.BILIBILI_PACKAGENAME, 0).lastUpdateTime;
                ObjectInputStream stream = new ObjectInputStream(new FileInputStream(hookInfoFile));

                if (stream.readLong() == lastUpdateTime)
                    mHookInfo = (Map<String, String>) stream.readObject();
            }

            long endTime = System.currentTimeMillis();
            Log.d(TAG, "Read hook info completed, cost " + (endTime - startTime) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Whether to update the serialization file.
     */
    private boolean checkHookInfo() {
        boolean needUpdate = false;

        if (mHookInfo == null) {
            mHookInfo = new HashMap<>();
            needUpdate = true;
        }
        if (!mHookInfo.containsKey("class_retrofit_response")) {
            mHookInfo.put("class_retrofit_response", findRetrofitResponseClass());
            needUpdate = true;
        }
        if (!mHookInfo.containsKey("class_fastjson")) {
            Class<?> fastJsonClass = findFastJsonClass();
            boolean notObfuscated = "JSON".equals(fastJsonClass.getSimpleName());
            mHookInfo.put("class_fastjson", fastJsonClass.getName());
            mHookInfo.put("method_fastjson_parse", notObfuscated ? "parseObject" : "a");
            needUpdate = true;
        }

        Log.d(TAG, "Check hook info completed: " + needUpdate);
        return needUpdate;
    }

    private void writeHookInfo(Context context) {
        try {
            File hookInfoFile = new File(context.getCacheDir(), Constant.HOOK_INFO_FILE_NAME);
            long lastUpdateTime = context.getPackageManager().getPackageInfo(Constant.BILIBILI_PACKAGENAME, 0).lastUpdateTime;

            if (hookInfoFile.exists()) hookInfoFile.delete();

            ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(hookInfoFile));
            stream.writeLong(lastUpdateTime);
            stream.writeObject(mHookInfo);
            stream.flush();
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Write hook info completed");
    }

    private String findRetrofitResponseClass() {
        Method[] methods = BiliBiliPackage.getInstance().bangumiApiResponse().getMethods();
        for (Method method : methods) {
            if ("extractResult".equals(method.getName())) {
                Class<?> responseClass = method.getParameterTypes()[0];
                return responseClass.getName();
            }
        }
        return null;
    }

    private Class<?> findFastJsonClass() {
        Class<?> clazz;
        try {
            clazz = findClass("com.alibaba.fastjson.JSON", mClassLoader);
        } catch (ClassNotFoundError e) {
            clazz = findClass("com.alibaba.fastjson.a", mClassLoader);
        }
        return clazz;
    }
}