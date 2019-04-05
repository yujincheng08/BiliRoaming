package me.iacn.biliroaming.hooker;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import me.iacn.biliroaming.network.BiliRoamingApi;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

/**
 * Created by iAcn on 2019/4/5
 * Email i@iacn.me
 */
public class Season404Hook extends BaseHook {

    private Map<String, String> lastSeasonInfo;

    public Season404Hook(ClassLoader classLoader) {
        super(classLoader);
        lastSeasonInfo = new HashMap<>();
    }

    @Override
    public void startHook() {
        System.out.println("startHook");

        Class<?> paramsMapClass = findClass(
                "com.bilibili.bangumi.api.uniform.BangumiDetailApiService$UniformSeasonParamsMap", mClassLoader);
        XposedBridge.hookAllConstructors(paramsMapClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                HashMap<String, String> paramMap = (HashMap) param.thisObject;
                lastSeasonInfo.put("season_id", paramMap.get("season_id"));
                lastSeasonInfo.put("access_key", paramMap.get("access_key"));
            }
        });

        findAndHookMethod("com.bilibili.okretro.BaseResponse", mClassLoader,
                "isSuccess", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        System.out.println("isSuccess, this = " + param.thisObject);

                        Class<?> clazz = findClass("com.bilibili.bangumi.api.BangumiApiResponse", mClassLoader);

                        if (!clazz.isInstance(param.thisObject)) return;

                        boolean is404 = getIntField(param.thisObject, "code") == -404;
                        boolean isNullResult = getObjectField(param.thisObject, "result") == null;

                        if (!is404 || !isNullResult) return;

                        String seasonId = lastSeasonInfo.get("season_id");

                        if (seasonId == null) return;


                        String content = BiliRoamingApi.getSeason(seasonId);
                        System.out.println(content.length());
                        JSONObject contentJson = new JSONObject(content);

                        if (contentJson.optInt("code") == 0) {

                            Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                            Class<?> beanClass = findClass(
                                    "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

                            System.out.println(beanClass);

                            JSONObject resultJson = contentJson.optJSONObject("result");
                            Object obj = callStaticMethod(fastJsonClass, "a", resultJson.toString(), beanClass);

                            System.out.println(obj);


                            setIntField(param.thisObject, "code", 0);
                            setObjectField(param.thisObject, "result", obj);
                        }
                    }
                });
    }
}