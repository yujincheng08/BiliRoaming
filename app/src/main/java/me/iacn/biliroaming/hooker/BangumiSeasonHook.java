package me.iacn.biliroaming.hooker;

import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.iacn.biliroaming.network.BiliRoamingApi;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BangumiSeasonHook extends BaseHook {

    private static final String TAG = "BiliRoaming";
    private Map<String, String> lastSeasonInfo;

    public BangumiSeasonHook(ClassLoader classLoader) {
        super(classLoader);
        lastSeasonInfo = new HashMap<>();
    }

    @Override
    public void startHook() {
        Log.d(TAG, "startHook: BangumiSeason");
        findAndHookMethod("com.bilibili.bangumi.viewmodel.detail.BangumiDetailViewModel$b", mClassLoader,
                "call", Object.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object bangumiSeason = param.args[0];

                        Log.d(TAG, "SeasonInformation: stock = " + bangumiSeason);

                        if (bangumiSeason == null) return;

                        List episodes = (List) getObjectField(bangumiSeason, "episodes");
                        Object rights = getObjectField(bangumiSeason, "rights");
                        boolean areaLimit = getBooleanField(rights, "areaLimit");

                        if (areaLimit && episodes.size() == 0) {
                            // It's a bangumi that limit watching area
                            String seasonId = (String) getObjectField(bangumiSeason, "seasonId");

                            Log.d(TAG, "Limited Bangumi: seasonTitle = " +
                                    getObjectField(bangumiSeason, "seasonTitle") +
                                    ", seasonId = " + seasonId);

                            String content = BiliRoamingApi.getSeason(seasonId);
                            JSONObject contentJson = new JSONObject(content);
                            int code = contentJson.optInt("code");

                            Log.d(TAG, "Get a season from proxy server, code = " + code);

                            if (contentJson.optInt("code") == 0) {
                                Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                                Class<?> beanClass = findClass(
                                        "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

                                JSONObject resultJson = contentJson.optJSONObject("result");
                                Object obj = callStaticMethod(fastJsonClass, "a", resultJson.toString(), beanClass);
                                Object newRights = getObjectField(obj, "rights");
                                Object newEpisodes = getObjectField(obj, "episodes");

                                setObjectField(bangumiSeason, "rights", newRights);
                                setObjectField(bangumiSeason, "episodes", newEpisodes);
                                setObjectField(bangumiSeason, "seasonLimit", null);

                                Log.d(TAG, "SeasonInformation: new = " + bangumiSeason
                                        + ", areaLimit = " + getBooleanField(newRights, "areaLimit")
                                        + ", episodes.size() = " + ((List) newEpisodes).size());
                            }
                        }
                    }
                });

        Class<?> paramsMapClass = findClass(
                "com.bilibili.bangumi.api.uniform.BangumiDetailApiService$UniformSeasonParamsMap", mClassLoader);
        XposedBridge.hookAllConstructors(paramsMapClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                HashMap<String, String> paramMap = (HashMap) param.thisObject;
                String seasonId = paramMap.get("season_id");
                String accessKey = paramMap.get("access_key");

                Log.d(TAG, "SeasonInfo: seasonId = " + seasonId + ", accessKey = " + accessKey);

                lastSeasonInfo.put("season_id", seasonId);
                lastSeasonInfo.put("access_key", accessKey);
            }
        });

        findAndHookMethod("com.bilibili.okretro.BaseResponse", mClassLoader,
                "isSuccess", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Class<?> bangumiApiResponse = findClass(
                                "com.bilibili.bangumi.api.BangumiApiResponse", mClassLoader);

                        // Filter non-bangumi responses
                        if (!bangumiApiResponse.isInstance(param.thisObject)) return;

                        boolean is404 = getIntField(param.thisObject, "code") == -404;
                        boolean isNullResult = getObjectField(param.thisObject, "result") == null;

                        // Filter normal bangumis
                        if (!is404 || !isNullResult) return;

                        String seasonId = lastSeasonInfo.get("season_id");

                        // Filter other request
                        // If it isn't bangumi, seasonId will be null
                        if (seasonId == null) return;

                        String content = BiliRoamingApi.getSeason(seasonId);
                        JSONObject contentJson = new JSONObject(content);

                        if (contentJson.optInt("code") == 0) {
                            Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                            Class<?> beanClass = findClass(
                                    "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

                            JSONObject resultJson = contentJson.optJSONObject("result");
                            Object obj = callStaticMethod(fastJsonClass, "a", resultJson.toString(), beanClass);

                            setIntField(param.thisObject, "code", 0);
                            setObjectField(param.thisObject, "result", obj);
                        }
                    }
                });
    }
}