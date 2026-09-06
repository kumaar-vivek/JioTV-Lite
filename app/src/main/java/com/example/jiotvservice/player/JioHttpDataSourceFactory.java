package com.example.jiotvservice.player;

import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import android.content.Context;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import com.example.jiotvservice.api.ApiProvider;
import com.example.jiotvservice.auth.DeviceInfoFactory;
import com.example.jiotvservice.session.SessionManager;
import com.example.jiotvservice.util.Logger;
import java.util.Map;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

@UnstableApi
public class JioHttpDataSourceFactory implements HttpDataSource.Factory {
    private final Context context;
    private final SessionManager session;
    private final OkHttpClient client;
    private final HttpDataSource.RequestProperties defaultRequestProperties;

    public JioHttpDataSourceFactory(Context context, SessionManager session) {
        this.context = context.getApplicationContext();
        this.session = session;
        this.client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(ApiProvider.COOKIE_MANAGER))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        this.defaultRequestProperties = new HttpDataSource.RequestProperties();
    }

    @Override
    public HttpDataSource createDataSource() {
        OkHttpDataSource dataSource = new OkHttpDataSource.Factory(client)
                .setUserAgent(getJioUserAgent())
                .createDataSource();
        
        applyJioHeaders(dataSource);

        for (Map.Entry<String, String> entry : defaultRequestProperties.getSnapshot().entrySet()) {
            dataSource.setRequestProperty(entry.getKey(), entry.getValue());
        }
        
        return dataSource;
    }

    private String getJioUserAgent() {
        return "JioTV/7.1.4 (Linux;Android " + android.os.Build.VERSION.RELEASE + ") ExoPlayerLib/1.5.1";
    }

    private void applyJioHeaders(HttpDataSource dataSource) {
        String sso = session.getSsoToken();
        String access = session.getAccessToken();
        String lb = session.getLbCookie();
        String unique = session.getUniqueId();
        String subId = session.getSubscriberId();
        String deviceId = DeviceInfoFactory.getAndroidId(context);

        if (sso != null) dataSource.setRequestProperty("ssotoken", sso);
        if (access != null) dataSource.setRequestProperty("accesstoken", access);
        if (lb != null) dataSource.setRequestProperty("lbcookies", lb);
        if (unique != null) dataSource.setRequestProperty("uniqueId", unique);
        if (subId != null) {
            // Use the same casing/names as the original player. HTTP header
            // names are case-insensitive, but matching the original makes
            // request diagnostics much easier.
            dataSource.setRequestProperty("subscriberId", subId);
            dataSource.setRequestProperty("crmid", subId);
        }
        dataSource.setRequestProperty("deviceId", deviceId);
        dataSource.setRequestProperty("os", "android");
        dataSource.setRequestProperty("osversion", android.os.Build.VERSION.RELEASE);
        dataSource.setRequestProperty("devicetype", "phone");
        dataSource.setRequestProperty("versioncode", "413");
        dataSource.setRequestProperty("usergroup", "tvYR7NSNn7rymo3F");
        dataSource.setRequestProperty("appkey", "NzNiMDhlYzQyNjJm");
    }

    @Override
    public HttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
        this.defaultRequestProperties.clear();
        this.defaultRequestProperties.set(defaultRequestProperties);
        return this;
    }
}
