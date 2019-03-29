package me.iacn.biliroaming.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by iAcn on 2019/3/27
 * Email i@iacn.me
 */
public class BiliRoamingApi {

    private static final String BILIROAMING_SEASON_URL = "https://api.iacn.me/biliroaming/season?";
    private static final String BILIROAMING_PLAYURL_URL = "https://api.iacn.me/biliroaming/playurl?";

    public static String getSeason(String seasonId) throws IOException {
        String urlString = BILIROAMING_SEASON_URL + "season_id=" + seasonId;
        return getContent(urlString);
    }

    public static String getPlayUrl(String queryString) throws IOException {
        String urlString = BILIROAMING_PLAYURL_URL + queryString;
        return getContent(urlString);
    }

    private static String getContent(String urlString) throws IOException {
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        InputStream inputStream = connection.getInputStream();
        String encoding = connection.getContentEncoding();

        return StreamUtils.getContent(inputStream, encoding);
    }
}