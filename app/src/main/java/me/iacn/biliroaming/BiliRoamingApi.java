package me.iacn.biliroaming;

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
        String apiUrl = BILIROAMING_SEASON_URL + "season_id=" + seasonId;
        URL url = new URL(apiUrl);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(4000);
        InputStream inputStream = connection.getInputStream();
        String encoding = connection.getContentEncoding();

        return StreamUtils.getContent(inputStream, encoding);
    }

    public String getPlayUrl() {
        return null;
    }
}