package com.example.jiotvservice.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import com.example.jiotvservice.R;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChannelLogoLoader {
    private static final String LOGO_BASE_URL =
            "https://jiotvimages.cdn.jio.com/dare_images/images/";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(64) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount() / 1024);
                }
            };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ChannelLogoLoader() {}

    public static void load(Context context, ImageView imageView, String logoUrl) {
        String url = buildLogoUrl(logoUrl);
        imageView.setTag(url);

        if (url.isEmpty()) {
            imageView.setImageDrawable(context.getDrawable(R.drawable.channel_placeholder));
            return;
        }

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageDrawable(context.getDrawable(R.drawable.channel_placeholder));
        EXECUTOR.execute(() -> {
            Bitmap bitmap = fetchBitmap(url);
            if (bitmap == null) {
                return;
            }

            CACHE.put(url, bitmap);
            MAIN.post(() -> {
                Object tag = imageView.getTag();
                if (url.equals(tag)) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static String buildLogoUrl(String logoUrl) {
        if (logoUrl == null) {
            return "";
        }

        String value = logoUrl.trim();
        if (value.isEmpty()) {
            return "";
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }

        return LOGO_BASE_URL + Uri.encode(value, "/");
    }

    private static Bitmap fetchBitmap(String url) {
        HttpURLConnection connection = null;
        InputStream input = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "okhttp/4.2.2");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (input != null) input.close();
            } catch (Exception ignored) {}
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
