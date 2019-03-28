package me.iacn.biliroaming.hooker;

import android.os.AsyncTask;
import android.text.TextUtils;

import org.json.JSONObject;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import me.iacn.biliroaming.BiliRoamingApi;

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

                        System.out.println("b call = " + bangumiSeason);
                        if (bangumiSeason == null) return;

                        List episodes = (List) getObjectField(bangumiSeason, "episodes");
                        Object rights = getObjectField(bangumiSeason, "rights");
                        boolean areaLimit = getBooleanField(rights, "areaLimit");

                        if (areaLimit && episodes.size() == 0) {
                            String seasonId = (String) getObjectField(bangumiSeason, "seasonId");


                            String jsonText = "{\"cover\":\"http://i0.hdslb.com/bfs/bangumi/becda7a59d0fe317c51a6e357857cffca20fa0d4.jpg\",\"episodes\":[{\"aid\":18281381,\"badge\":\"\",\"badge_type\":0,\"cid\":29892777,\"cover\":\"http://i2.hdslb.com/bfs/archive/ff16158e2f3434cf461606beb235d723ac63a44c.jpg\",\"dimension\":{\"height\":0,\"rotate\":0,\"width\":0},\"from\":\"bangumi\",\"id\":183799,\"long_title\":\"兩位孤獨者\",\"share_url\":\"https://m.bilibili.com/bangumi/play/ep183799\",\"short_link\":\"https://b23.tv/ep183799\",\"status\":2,\"title\":\"1\",\"vid\":\"\"},{\"aid\":18520148,\"badge\":\"\",\"badge_type\":0,\"cid\":30219063,\"cover\":\"http://i0.hdslb.com/bfs/archive/86594b935935a4dfbb7ab318e8063779cf7a3757.jpg\",\"dimension\":{\"height\":0,\"rotate\":0,\"width\":0},\"from\":\"bangumi\",\"id\":183800,\"long_title\":\"連接的意義\",\"share_url\":\"https://m.bilibili.com/bangumi/play/ep183800\",\"short_link\":\"https://b23.tv/ep183800\",\"status\":2,\"title\":\"2\",\"vid\":\"\"}],\"evaluate\":\"遥远的未来，人类在荒废的大地上建设了移动要塞都市“种植园”，并讴歌着文明。在那当中建造的驾驶员居住设施“米斯特汀”，通称“鸟笼”。孩子们就住在那里，他们被告知的使命，只有战斗。敌人是一切都被谜团覆盖的...\",\"link\":\"http://www.bilibili.com/bangumi/media/md9192/\",\"media_id\":9192,\"mode\":2,\"new_ep\":{\"desc\":\"已完结, 全24话\",\"id\":216076,\"is_new\":0,\"title\":\"24\"},\"paster\":{\"aid\":0,\"allow_jump\":0,\"cid\":0,\"duration\":0,\"type\":0,\"url\":\"\"},\"payment\":{\"pay_tip\":{},\"pay_type\":{\"allow_ticket\":0},\"price\":\"0.0\",\"vip_promotion\":\"\"},\"publish\":{\"is_finish\":1,\"is_started\":1,\"pub_time\":\"2018-01-14 01:00:00\",\"pub_time_show\":\"01月14日01:00\",\"weekday\":0},\"rating\":{\"count\":13787,\"score\":8.7},\"record\":\"\",\"rights\":{\"allow_bp\":0,\"allow_download\":1,\"allow_review\":1,\"area_limit\":0,\"ban_area_show\":1,\"copyright\":\"bilibili\",\"is_preview\":0,\"watch_platform\":0},\"season_id\":21680,\"season_title\":\"DARLING in the FRANXX（僅限港澳台地區）\",\"seasons\":[{\"is_new\":0,\"season_id\":21680,\"season_title\":\"TV\"}],\"section\":[],\"series\":{\"series_id\":3878,\"series_title\":\"DARLING in the FRANXX\"},\"share_url\":\"http://m.bilibili.com/bangumi/play/ss21680\",\"short_link\":\"https://b23.tv/ss21680\",\"square_cover\":\"http://i0.hdslb.com/bfs/bangumi/7478441c386b808aca0bb2fee2e07385db430d51.jpg\",\"stat\":{\"coins\":33850,\"danmakus\":205106,\"favorites\":607545,\"reply\":59437,\"share\":6048,\"views\":4046730},\"status\":2,\"title\":\"DARLING in the FRANXX（僅限港澳台地區）\",\"total\":24,\"type\":1,\"user_status\":{\"follow\":0,\"pay\":0,\"sponsor\":0,\"vip\":0,\"vip_frozen\":0}}";

                            Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                            Class<?> beanClass = findClass("com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

                            TestThread testThread = new TestThread(bangumiSeason);
                            testThread.start();
                            testThread.join();
                            System.out.println("222222222222222222222");
                        }
                    }
                });

        Class<?> activityClass = findClass(
                "com.bilibili.bangumi.ui.detail.BangumiDetailActivity", mClassLoader);
        Class<?> seasonClass = findClass(
                "com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);

        findAndHookMethod(activityClass, "a", seasonClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object bangumiSeason = param.args[0];

                System.out.println("a call = " + bangumiSeason);
                if (bangumiSeason == null) return;

                List episodes = (List) getObjectField(bangumiSeason, "episodes");
                Object rights = getObjectField(bangumiSeason, "rights");
                boolean areaLimit = getBooleanField(rights, "areaLimit");

                if (areaLimit && episodes.size() == 0) {
                    String seasonId = (String) getObjectField(bangumiSeason, "seasonId");

                    /*TestThread testThread = new TestThread();
                    testThread.start();
                    testThread.join();
                    System.out.println("222222222222222222222");*/

                    new SeasonTask(mClassLoader).execute(seasonId, bangumiSeason);

                    synchronized (bangumiSeason) {
                        bangumiSeason.wait();
                    }
                }
            }
        });
    }

    class TestThread extends Thread {

        private Object bangumiSeason;

        public TestThread(Object obj) {
            this.bangumiSeason = obj;
        }

        @Override
        public void run() {
            try {
                String seasonId = (String) getObjectField(bangumiSeason, "seasonId");
                String content = BiliRoamingApi.getSeason(seasonId);
                JSONObject json = new JSONObject(content);

                if (json.optInt("code") == 0) {
                    JSONObject resultJson = json.optJSONObject("result");

                    Class<?> fastJsonClass = findClass("com.alibaba.fastjson.a", mClassLoader);
                    Class<?> beanClass = findClass("com.bilibili.bangumi.api.uniform.BangumiUniformSeason", mClassLoader);
                    String str = resultJson.toString();

                    Object obj = callStaticMethod(fastJsonClass, "a", str, beanClass);
                    System.out.println(obj);

                    Object newEpisodes = getObjectField(obj, "episodes");
                    Object newRights = getObjectField(obj, "rights");

                    setObjectField(bangumiSeason, "episodes", newEpisodes);
                    setObjectField(bangumiSeason, "rights", newRights);
                }

                System.out.println("111111111111111111");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class SeasonTask extends AsyncTask<Object, Void, Void> {

        private ClassLoader classLoader;

        SeasonTask(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        protected Void doInBackground(Object... args) {
            String seasonId = (String) args[0];
            Object bangumiSeason = args[1];

            synchronized (bangumiSeason) {
                if (!TextUtils.isEmpty(seasonId)) {
                    try {
                        String content = BiliRoamingApi.getSeason(seasonId);
                        JSONObject json = new JSONObject(content);
                        if (json.optInt("code") == 0) {


                            JSONObject resultJson = json.optJSONObject("result");

                            setRightsForOriginal(bangumiSeason, resultJson);
                            setEpisodesForOriginal(bangumiSeason, resultJson);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        System.out.println("111111111111111111");
                        bangumiSeason.notify();
                    }
                }
            }
            return null;
        }

        private void setRightsForOriginal(Object bangumiSeason, JSONObject json) {

        }

        private void setEpisodesForOriginal(Object bangumiSeason, JSONObject json) throws Exception {

        }
    }
}