package me.iacn.biliroaming.hook;

import android.util.ArrayMap;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.iacn.biliroaming.BiliBiliPackage;
import me.iacn.biliroaming.XposedInit;
import me.iacn.biliroaming.network.BiliRoamingApi;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static me.iacn.biliroaming.Constant.TAG;
import static me.iacn.biliroaming.Constant.TYPE_EPISODE_ID;
import static me.iacn.biliroaming.Constant.TYPE_SEASON_ID;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BangumiSeasonHook extends BaseHook {

    private Map<String, Object> lastSeasonInfo;

    public BangumiSeasonHook(ClassLoader classLoader) {
        super(classLoader);
        lastSeasonInfo = new ArrayMap<>();
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("main_func", false)) return;
        Log.d(TAG, "startHook: BangumiSeason");

        Class<?> paramsMapClass = findClass("com.bilibili.bangumi.data.page.detail." +
                "BangumiDetailApiService$UniformSeasonParamsMap", mClassLoader);
        XposedBridge.hookAllConstructors(paramsMapClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Map<String, String> paramMap = (Map) param.thisObject;
                int type = (int) param.args[1];

                switch (type) {
                    case TYPE_SEASON_ID:
                        String seasonId = paramMap.get("season_id");
                        lastSeasonInfo.put("season_id", seasonId);
                        Log.d(TAG, "Bangumi watching: seasonId = " + seasonId);
                        break;
                    case TYPE_EPISODE_ID:
                        String episodeId = paramMap.get("ep_id");
                        lastSeasonInfo.put("episode_id", episodeId);
                        Log.d(TAG, "Bangumi watching: episodeId = " + episodeId);
                        break;
                    default:
                        return;
                }
                lastSeasonInfo.put("type", type);

                String accessKey = paramMap.get("access_key");
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
                // If it isn't bangumi, the type variable will not exist in this map
                if (!bangumiApiResponse.isInstance(body) || !lastSeasonInfo.containsKey("type"))
                    return;

                Object result = getObjectField(body, "result");
                // Filter normal bangumi and other responses
                if (isBangumiWithWatchPermission(getIntField(body, "code"), result)) {
                    lastSeasonInfo.clear();
                    return;
                }

                boolean useCache = result != null;
                String content = getSeasonInfoFromProxyServer(useCache);
                JSONObject contentJson = new JSONObject(content);
                int code = contentJson.optInt("code");

                Log.d(TAG, "Get new season information from proxy server: code = " + code
                        + ", useCache = " + useCache);

                if (code == 0) {
                    Class<?> fastJsonClass = BiliBiliPackage.getInstance().fastJson();
                    Class<?> beanClass = BiliBiliPackage.getInstance().bangumiUniformSeason();

                    JSONObject resultJson = contentJson.optJSONObject("result");
                    Object newResult = callStaticMethod(fastJsonClass,
                            BiliBiliPackage.getInstance().fastJsonParse(), resultJson.toString(), beanClass);

                    if (useCache) {
                        // Replace only episodes and rights
                        // Remain user information, such as follow status, watch progress, etc.
                        Object newRights = getObjectField(newResult, "rights");
                        if (!getBooleanField(newRights, "areaLimit")) {
                            Object newEpisodes = getObjectField(newResult, "episodes");
                            setObjectField(result, "rights", newRights);
                            setObjectField(result, "episodes", newEpisodes);
                            setObjectField(result, "seasonLimit", null);

                            if (BiliBiliPackage.getInstance().hasModulesInResult()) {
                                Object newModules = getObjectField(newResult, "modules");
                                setObjectField(result, "modules", newModules);
                            }
                        }
                    } else {
                        setIntField(body, "code", 0);
                        setObjectField(body, "result", newResult);
                    }
                }

                lastSeasonInfo.clear();
            }
        });
    }

    private boolean isBangumiWithWatchPermission(int code, Object result) {
        Log.d(TAG, "BangumiApiResponse: code = " + code + ", result = " + result);

        if (result != null) {
            Class<?> bangumiSeasonClass = BiliBiliPackage.getInstance().bangumiUniformSeason();
            if (bangumiSeasonClass.isInstance(result)) {
                Object rights = getObjectField(result, "rights");
                boolean areaLimit = getBooleanField(rights, "areaLimit");
                return !areaLimit;
            }
        }
        return code != -404;
    }

    private String getSeasonInfoFromProxyServer(boolean useCache) throws IOException {
        Log.d(TAG, "Found a restricted bangumi: " + lastSeasonInfo);

        String id = null;
        String accessKey = (String) lastSeasonInfo.get("access_key");

        switch ((int) lastSeasonInfo.get("type")) {
            case TYPE_SEASON_ID:
                id = (String) lastSeasonInfo.get("season_id");
                break;
            case TYPE_EPISODE_ID:
                id = "ep" + lastSeasonInfo.get("episode_id");
                break;
        }

        return BiliRoamingApi.getSeason(id, accessKey, useCache);
    }
}