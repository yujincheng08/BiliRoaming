package me.iacn.biliroaming;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class BangumiInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        System.out.println("Network request = " + request.url());
        return chain.proceed(request);
    }
}
