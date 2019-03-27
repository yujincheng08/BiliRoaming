package me.iacn.biliroaming.hooker;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

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
        Class<?> activityClass = findClass(
                "com.bilibili.bangumi.ui.detail.BangumiDetailActivity", mClassLoader);
        Class<?> seasonClass = findClass(
                "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

        findAndHookMethod(activityClass, "a", seasonClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object bangumiSeason = param.args[0];
                List episodes = (List) getObjectField(bangumiSeason, "episodes");
                Object rights = getObjectField(bangumiSeason, "rights");
                boolean areaLimit = getBooleanField(rights, "areaLimit");

                if (areaLimit && episodes.size() == 0) {
                    System.out.println("Limited Bangumi: seasonTitle = " +
                            getObjectField(bangumiSeason, "seasonTitle") +
                            ", seasonId = " + getObjectField(bangumiSeason, "seasonId"));
                }
            }
        });
    }
}