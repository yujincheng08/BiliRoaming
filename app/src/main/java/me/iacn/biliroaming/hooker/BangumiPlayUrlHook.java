package me.iacn.biliroaming.hooker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.network.BiliRoamingApi;
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
                        String urlString = connection.getURL().toString();

                        if (urlString.startsWith("https://api.bilibili.com/pgc/player/api/playurl")) {
                            String queryString = urlString.substring(urlString.indexOf("?") + 1);
                            if (queryString.contains("module=bangumi")) {
                                InputStream inputStream = (InputStream) param.getResult();
                                System.out.println(inputStream.getClass());
                                String encoding = connection.getContentEncoding();
                                String content = StreamUtils.getContent(inputStream, encoding);

                                if (isLimitWatchingArea(content)) {
                                    String newContent = BiliRoamingApi.getPlayUrl(queryString);
                                    param.setResult(new ByteArrayInputStream(newContent.getBytes()));
                                }
                            }
                        }
                    }
                });
    }

    private boolean isLimitWatchingArea(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText);
            int code = json.optInt("code");
            return code == -10403;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
}