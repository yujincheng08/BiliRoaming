package me.iacn.biliroaming.network;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import me.iacn.biliroaming.BuildConfig;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BiliRoamingApi {

    private static final String BILIROAMING_SEASON_URL = "api.iacn.me/biliroaming/season";
    private static final String BILIROAMING_PLAYURL_URL = "api.iacn.me/biliroaming/playurl";
    private static final String BILIPLUS_SEASON_URL = "www.biliplus.com/api/bangumi";
    private static final String BILIPLUS_PLAYURL_URL = "www.biliplus.com/BPplayurl.php";

    private static final String BILIEPISODE_TEMPLATE = "{\"aid\":0,\"badge\":\"\",\"badge_type\":0,\"cid\":0,\"cover\":\"\",\"dimension\":{\"height\":1080,\"rotate\":0,\"width\":1920},\"from\":\"bangumi\",\"id\":0,\"long_title\":\"\",\"release_date\":\"\",\"rights\":{\"allow_dm\":1},\"share_copy\":\"\",\"share_url\":\"\",\"short_link\":\"\",\"stat\":{\"coin\":0,\"danmakus\":0,\"play\":0,\"reply\":0},\"status\":0,\"subtitle\":\"\",\"title\":\"\",\"vid\":\"\"}";
    private static final String BILIEPRIGHT_TEMPLATE = "{\"allow_bp\":0,\"allow_bp_rank\":0,\"allow_download\":1,\"allow_review\":1,\"area_limit\":0,\"ban_area_show\":1,\"can_watch\":1,\"copyright\":\"bilibili\",\"is_cover_show\":0,\"is_preview\":0,\"watch_platform\":0}";

    public static String getSeason(String id, String accessKey, boolean useCache) throws IOException {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https").encodedAuthority(BILIROAMING_SEASON_URL).appendPath(id);

        if (!TextUtils.isEmpty(accessKey)) {
            builder.appendQueryParameter("access_key", accessKey);
        }
        builder.appendQueryParameter("use_cache", useCache ? "1" : "0");

        return getContent(builder.toString());
    }

    public static String getPlayUrl(String queryString) throws IOException {
        return getContent("https://" + BILIROAMING_PLAYURL_URL + "?" + queryString);
    }

    public static String getPlayUrl_BP(String queryString) throws IOException {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https").encodedAuthority(BILIPLUS_PLAYURL_URL);
        builder.encodedQuery(queryString);
        builder.appendQueryParameter("module", "bangumi");
        builder.appendQueryParameter("otype", "json");
        builder.appendQueryParameter("platform", "android");
        return getContent(builder.toString());
    }

    private static String getContent(String urlString) throws IOException {
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Build", String.valueOf(BuildConfig.VERSION_CODE));
        connection.setConnectTimeout(4000);
        connection.connect();

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            InputStream inputStream = connection.getInputStream();
            String encoding = connection.getContentEncoding();
            return StreamUtils.getContent(inputStream, encoding);
        }
        return null;
    }

    public static String getSeason_BP(String id) throws IOException {
        /*
        This won't work in android 7.0
        The ciper suite that BiliPlus used is not supported in android 7.0.

        A known bug:
        https://stackoverflow.com/questions/39133437/sslhandshakeexception-handshake-failed-on-android-n-7-0
        https://code.google.com/p/android/issues/detail?id=224438
        */
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("https").encodedAuthority(BILIPLUS_SEASON_URL);
        builder.appendQueryParameter("season", id);
        String ret = getContent(builder.toString());

        try {
            JSONObject season_bpt = new JSONObject(ret);
            JSONArray season_bp = season_bpt.getJSONObject("result").getJSONArray("episodes");
            JSONArray season_ret = new JSONArray();
            for (int i = season_bp.length() - 1; i >= 0; i--) {
                JSONObject ep = season_bp.getJSONObject(i);
                JSONObject nep = new JSONObject(BILIEPISODE_TEMPLATE);
                nep.put("aid", ep.getString("av_id"));
                nep.put("cid", ep.getString("danmaku"));
                nep.put("cover", ep.getString("cover"));
                nep.put("id", ep.getString("episode_id"));
                nep.put("long_title", ep.getString("index_title"));
                nep.put("status", ep.getString("episode_status"));
                if (ep.getString("episode_status").equals("13")) {
                    nep.put("badge", "会员");
                }
                nep.put("title", ep.getString("index"));
                season_ret.put(nep);
            }
            JSONObject ep_ret = new JSONObject();
            JSONObject ep_ret_result = new JSONObject();
            ep_ret.put("code", 0);
            ep_ret.put("message", "success");
            ep_ret_result.put("episodes", season_ret);
            JSONObject rp_ret_result_rights = new JSONObject(BILIEPRIGHT_TEMPLATE);
            ep_ret_result.put("rights", rp_ret_result_rights);
            ep_ret.put("result", ep_ret_result);

            return ep_ret.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{code:1}";
        }
    }
}
