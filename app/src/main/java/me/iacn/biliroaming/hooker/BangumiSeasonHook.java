package me.iacn.biliroaming.hooker;

import android.util.Log;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.iacn.biliroaming.BiliBiliPackage;
import me.iacn.biliroaming.network.BiliRoamingApi;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BangumiSeasonHook extends BaseHook {

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

        Class<?> responseClass = findClass(BiliBiliPackage.getInstance().retrofitResponse(), mClassLoader);
        XposedBridge.hookAllConstructors(responseClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object body = param.args[1];
                Class<?> bangumiApiResponse = BiliBiliPackage.getInstance().bangumiApiResponse();

                // Filter non-bangumi responses
                if (!bangumiApiResponse.isInstance(body)) return;

                if (isNormalSeason(body)) return;

                String seasonId = lastSeasonInfo.get("season_id");
                String accessKey = lastSeasonInfo.get("access_key");

                // Filter other request
                // If it isn't bangumi, seasonId will be null
                if (seasonId == null) return;

                Log.d(TAG, "Limited Bangumi: seasonId = " + seasonId);

                String content = BiliRoamingApi.getSeason(seasonId, accessKey);
                JSONObject contentJson = new JSONObject(content);
                int code = contentJson.optInt("code");

                Log.d(TAG, "Get a season from proxy server, code = " + code);

                if (code == 0) {
                    Class<?> fastJsonClass = BiliBiliPackage.getInstance().fastJson();
                    Class<?> beanClass = BiliBiliPackage.getInstance().bangumiUniformSeason();

                    JSONObject resultJson = contentJson.optJSONObject("result");
                    Object newResult = callStaticMethod(fastJsonClass,
                            BiliBiliPackage.getInstance().fastJsonParse(), resultJson.toString(), beanClass);

                    setIntField(body, "code", 0);
                    setObjectField(body, "result", newResult);
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

        if (code == -404 && result == null) {
            Log.d(TAG, "SeasonResponse: code = " + code + ", result = null");
            return false;
        }

        Class<?> bangumiSeasonClass = BiliBiliPackage.getInstance().bangumiUniformSeason();
        if (bangumiSeasonClass.isInstance(result)) {
            Log.d(TAG, "SeasonResponse: code = " + code + ", result = " + result);

            List episodes = (List) getObjectField(result, "episodes");
            Object rights = getObjectField(result, "rights");
            boolean areaLimit = getBooleanField(rights, "areaLimit");

            return !areaLimit && episodes.size() != 0;
        }

        return true;
    }
}