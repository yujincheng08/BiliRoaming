package me.iacn.biliroaming.hooker;

import org.json.JSONObject;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.network.BiliRoamingApi;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BangumiSeasonHook extends BaseHook {

    public BangumiSeasonHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        findAndHookMethod("com.bilibili.bangumi.viewmodel.detail.BangumiDetailViewModel$b", mClassLoader,
                "call", Object.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object bangumiSeason = param.args[0];

                        if (bangumiSeason == null) return;

                        List episodes = (List) getObjectField(bangumiSeason, "episodes");
                        Object rights = getObjectField(bangumiSeason, "rights");
                        boolean areaLimit = getBooleanField(rights, "areaLimit");

                        if (areaLimit && episodes.size() == 0) {
                            // It's a bangumi that limit watching area
                            String seasonId = (String) getObjectField(bangumiSeason, "seasonId");
                            String content = BiliRoamingApi.getSeason(seasonId);

                            JSONObject contentJson = new JSONObject(content);
                            if (contentJson.optInt("code") == 0) {
                                Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                                Class<?> beanClass = findClass(
                                        "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

                                JSONObject resultJson = contentJson.optJSONObject("result");
                                Object obj = callStaticMethod(fastJsonClass, "a", resultJson.toString(), beanClass);

                                Object newEpisodes = getObjectField(obj, "episodes");
                                Object newRights = getObjectField(obj, "rights");
                                setObjectField(bangumiSeason, "episodes", newEpisodes);
                                setObjectField(bangumiSeason, "rights", newRights);
                            }
                        }
                    }
                });
    }
}