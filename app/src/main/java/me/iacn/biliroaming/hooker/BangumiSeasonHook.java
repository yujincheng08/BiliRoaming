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

        Class<?> paramsMapClass = findClass(
                "com.bilibili.bangumi.api.uniform.BangumiDetailApiService$UniformSeasonParamsMap", mClassLoader);
        XposedBridge.hookAllConstructors(paramsMapClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                HashMap<String, String> paramMap = (HashMap) param.thisObject;
                String seasonId = paramMap.get("season_id");
                String accessKey = paramMap.get("access_key");

                Log.d(TAG, "SeasonInformation: seasonId = " + seasonId);

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

                        if (isNormalSeason(param.thisObject)) return;

                        String seasonId = lastSeasonInfo.get("season_id");

                        // Filter other request
                        // If it isn't bangumi, seasonId will be null
                        if (seasonId == null) return;

                        Log.d(TAG, "Limited Bangumi: seasonId = " + seasonId);

                        String content = BiliRoamingApi.getSeason(seasonId);
                        JSONObject contentJson = new JSONObject(content);
                        int code = contentJson.optInt("code");

                        Log.d(TAG, "Get a season from proxy server, code = " + code);

                        if (code == 0) {
                            Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                            Class<?> beanClass = findClass(
                                    "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

                            JSONObject resultJson = contentJson.optJSONObject("result");
                            Object newResutl = callStaticMethod(fastJsonClass, "a", resultJson.toString(), beanClass);

                            setIntField(param.thisObject, "code", 0);
                            setObjectField(param.thisObject, "result", newResutl);
                        }
                    }
                });

        findAndHookMethod("com.bilibili.bangumi.viewmodel.detail.BangumiDetailViewModel$b", mClassLoader,
                "call", Object.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        lastSeasonInfo.clear();
                    }
                });
    }

    private boolean isNormalSeason(Object bangumiApiResponse) {
        int code = getIntField(bangumiApiResponse, "code");
        Object result = getObjectField(bangumiApiResponse, "result");

        Log.d(TAG, "SeasonResponse: code = " + code + ", result = " + result);

        if (code == -404 && result == null) {
            return false;
        }

        List episodes = (List) getObjectField(result, "episodes");
        Object rights = getObjectField(result, "rights");
        boolean areaLimit = getBooleanField(rights, "areaLimit");

        return !areaLimit && episodes.size() != 0;
    }
}