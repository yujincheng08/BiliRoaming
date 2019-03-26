package me.iacn.biliroaming;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Created by iAcn on 2019/3/26
 * Email i@iacn.me
 */
public class StreamUtils {

    public static String getContent(InputStream inputStream, String encoding) {
        String result = null;
        ByteArrayOutputStream byteArrayStream = null;

        try {
            if ("gzip".equalsIgnoreCase(encoding))
                inputStream = new GZIPInputStream(inputStream);

            byte[] buffer = new byte[2048];
            byteArrayStream = new ByteArrayOutputStream();
            for (int len; (len = inputStream.read(buffer)) > 0; ) {
                byteArrayStream.write(buffer, 0, len);
            }

            byteArrayStream.flush();
            result = new String(byteArrayStream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) inputStream.close();

                if (byteArrayStream != null) byteArrayStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }
}