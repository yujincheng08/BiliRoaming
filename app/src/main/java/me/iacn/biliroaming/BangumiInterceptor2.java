package me.iacn.biliroaming;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedHelpers;

/**
 * Created by iAcn on 2019/3/24
 * Email i@iacn.me
 */
public class BangumiInterceptor2 implements InvocationHandler {

    private ClassLoader mClassLoader;

    public BangumiInterceptor2(ClassLoader classLoader) {
        this.mClassLoader = classLoader;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args.length == 1) {
            Object chain = args[0];

            Class<?> chainClass = chain.getClass();
            Class<?> requestClass = XposedHelpers.findClass("okhttp3.y", mClassLoader);
            Method requestMethod = XposedHelpers.findMethodExact(chainClass, "a");
            Method proceedMethod = XposedHelpers.findMethodExact(chainClass, "a", requestClass);

            Object request = requestMethod.invoke(chain);
            Object httpUrlObject = XposedHelpers.callMethod(request, "a");
            String url = (String) XposedHelpers.callMethod(httpUrlObject, "toString");
            if (url.contains("/pgc/view/app/season")) {
                System.out.println("url2 = " + url);
            }


            return proceedMethod.invoke(chain, request);
        }

        return null;
    }
}