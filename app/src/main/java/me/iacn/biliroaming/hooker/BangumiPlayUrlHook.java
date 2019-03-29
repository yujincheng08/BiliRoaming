package me.iacn.biliroaming.hooker;

import java.io.InputStream;
import java.net.HttpURLConnection;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.network.StreamUtils;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BangumiPlayUrlHook extends BaseHook {

    public BangumiPlayUrlHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        findAndHookMethod("com.bilibili.lib.okhttp.huc.OkHttpURLConnection", mClassLoader,
                "getInputStream", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Found from "b.ecy" in version 5.39.1
                        HttpURLConnection connection = (HttpURLConnection) param.thisObject;
//                        System.out.println(connection.getURL());

                        InputStream inputStream = (InputStream) param.getResult();
                        String encoding = connection.getContentEncoding();
                        String content = StreamUtils.getContent(inputStream, encoding);

//                                    System.out.println(content);
                    }
                });
    }
}