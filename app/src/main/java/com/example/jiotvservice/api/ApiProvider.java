package com.example.jiotvservice.api;

import com.example.jiotvservice.util.Logger;
import com.example.jiotvservice.BuildConfig;
import com.example.jiotvservice.JioTvApplication;
import com.example.jiotvservice.auth.DeviceInfoFactory;
import com.example.jiotvservice.model.AuthModels;
import com.example.jiotvservice.model.Channel;
import com.example.jiotvservice.session.SessionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiProvider {
    private static final String TAG = "MYJIO";
    public static final String JIOTV_API_BASE = "https://jiotvapi.media.jio.com/";
    public static final String REFRESH_ACCESS_TOKEN_URL = "https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken?langId=6";
    private static volatile JioTvApiService tvService;
    private static volatile AuthApiService authService;
    private static volatile okhttp3.OkHttpClient httpClient;
    public static final CookieManager COOKIE_MANAGER = new CookieManager();
    private static final OkHttpClient AUTH_CLIENT = new OkHttpClient.Builder().build();
    private static final ReentrantLock REFRESH_LOCK = new ReentrantLock();

    static {
        COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    }

    private ApiProvider() {}

    public static okhttp3.OkHttpClient client() {
        if (httpClient != null) return httpClient;
        synchronized (ApiProvider.class) {
            if (httpClient != null) return httpClient;
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .cookieJar(new JavaNetCookieJar(COOKIE_MANAGER))
                    .followRedirects(false) // Manual handling to preserve custom headers
                    .followSslRedirects(false);

            // BODY logging is extremely expensive for TV streaming/network traffic. Keep it only
            // in the DEBUG build used from Android Studio; release APKs have no HTTP debug logger.
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> {
                    int maxLogSize = 3500;
                    int length = message.length();
                    for (int offset = 0; offset < length; offset += maxLogSize) {
                        Logger.d(TAG, message.substring(offset, Math.min(length, offset + maxLogSize)));
                    }
                });
                logging.setLevel(HttpLoggingInterceptor.Level.BODY);
                clientBuilder.addInterceptor(logging);
            }

            httpClient = clientBuilder
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        SessionManager session = new SessionManager(JioTvApplication.getContext());
                        
                        if (session.isLoggedIn()) {
                            if (session.isAccessTokenExpired() || session.isSsoTokenExpired()) {
                                performFullRefresh(JioTvApplication.getContext());
                            }
                        }

                        request = applySessionHeaders(request, session);
                        okhttp3.Response response = chain.proceed(request);

                        int redirectCount = 0;
                        while (response.isRedirect() && redirectCount < 10) {
                            String location = response.header("Location");
                            if (location == null) break;
                            
                            okhttp3.HttpUrl newUrl = request.url().resolve(location);
                            if (newUrl == null) break;

                            Logger.d(TAG, "Redirect (" + response.code() + "): " + request.url() + " -> " + newUrl);
                            
                            request = request.newBuilder().url(newUrl).build();
                            request = applySessionHeaders(request, session);
                            
                            response.close();
                            response = chain.proceed(request);
                            redirectCount++;
                        }

                        if (response.code() == 419) {
                            if (performFullRefresh(JioTvApplication.getContext())) {
                                response.close();
                                return chain.proceed(applySessionHeaders(request.newBuilder().build(), session));
                            }
                        }
                        return response;
                    })
                    .build();
        }
        return httpClient;
    }

    private static Request applySessionHeaders(Request request, SessionManager session) {
        if (!session.isLoggedIn()) return request;
        Request.Builder builder = request.newBuilder();
        String sso = session.getSsoToken();
        String access = session.getAccessToken();
        String lb = session.getLbCookie();
        String unique = session.getUniqueId();
        String deviceId = DeviceInfoFactory.getAndroidId(JioTvApplication.getContext());

        if (sso != null) builder.header("ssotoken", sso);
        if (access != null) builder.header("accesstoken", access);
        if (lb != null) builder.header("lbcookies", lb);
        if (unique != null) builder.header("uniqueId", unique);
        builder.header("deviceId", deviceId);
        return builder.build();
    }

    public static boolean performFullRefresh(android.content.Context context) {
        REFRESH_LOCK.lock();
        try {
            SessionManager session = new SessionManager(context);
            if (!session.isAccessTokenExpired() && !session.isSsoTokenExpired()) return true;
            if (!performSsoTokenRefresh(context)) return false;
            if (!performAccessTokenRefresh(context)) return false;
            performBeginSession(context);
            return true;
        } finally {
            REFRESH_LOCK.unlock();
        }
    }

    public static boolean performBeginSession(android.content.Context context) {
        SessionManager session = new SessionManager(context);
        String lb = session.getLbCookie();
        if (lb == null) return false;
        try {
            Request req = new Request.Builder()
                    .url(JIOTV_API_BASE + "userservice/apis/v1.3/beginsession/begin")
                    .header("lbcookies", lb)
                    .header("ssotoken", session.getSsoToken())
                    .header("devicetype", "phone").header("os", "android").header("versioncode", "413")
                    .build();
            try (okhttp3.Response res = AUTH_CLIENT.newCall(req).execute()) {
                return res.isSuccessful();
            }
        } catch (Exception e) { 
            Logger.e(TAG, "Begin Session failed", e);
            return false; 
        }
    }

    public static boolean performSsoTokenRefresh(android.content.Context context) {
        SessionManager session = new SessionManager(context);
        String sso = session.getSsoToken();
        String unique = session.getUniqueId();
        if (sso == null || unique == null) return false;
        try {
            Request req = new Request.Builder()
                    .url(JioTvApiService.REFRESH_SSO_URL)
                    .header("uniqueid", unique).header("ssotoken", sso)
                    .header("os", "android").header("devicetype", JioTvApiService.DEVICE_TYPE)
                    .header("deviceid", DeviceInfoFactory.getAndroidId(context)).header("versioncode", "413")
                    .build();
            try (okhttp3.Response res = AUTH_CLIENT.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    AuthModels.AuthResponse auth = AuthModels.AuthResponse.fromRawJson(res.body().string());
                    if (auth != null && auth.getSsoToken() != null) {
                        Logger.d(TAG, "SSO Token refreshed: " + auth.getSsoToken().substring(0, 10) + "...");
                        session.saveSsoToken(auth.getSsoToken());
                        session.saveLbCookie(auth.getLbCookie());
                        return true;
                    }
                } else {
                    Logger.e(TAG, "SSO Refresh failed: HTTP " + res.code());
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "SSO Token refresh failed", e);
        }
        return false;
    }

    public static boolean performAccessTokenRefresh(android.content.Context context) {
        SessionManager session = new SessionManager(context);
        String refresh = session.getRefreshToken();
        if (refresh == null) return false;
        try {
            String body = new Gson().toJson(new AuthModels.RefreshAuthTokenRequest("RJIL_JioTV", DeviceInfoFactory.getAndroidId(context), refresh));
            Request req = new Request.Builder()
                    .url(REFRESH_ACCESS_TOKEN_URL)
                    .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json")))
                    .header("accesstoken", session.getAccessToken() != null ? session.getAccessToken() : "")
                    .header("devicetype", "phone").header("versioncode", "413").header("os", "android")
                    .build();
            try (okhttp3.Response res = AUTH_CLIENT.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    AuthModels.AuthResponse auth = AuthModels.AuthResponse.fromRawJson(res.body().string());
                    if (auth != null && auth.getAccessToken() != null) {
                        Logger.d(TAG, "Access Token refreshed successfully");
                        session.saveAccessToken(auth.getAccessToken());
                        return true;
                    }
                } else {
                    Logger.e(TAG, "Access Token refresh failed: HTTP " + res.code());
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Access Token refresh failed", e);
        }
        return false;
    }

    public static JioTvApiService get() {
        if (tvService == null) synchronized (ApiProvider.class) {
            if (tvService == null) tvService = retrofit(JIOTV_API_BASE).create(JioTvApiService.class);
        }
        return tvService;
    }

    public static AuthApiService auth() {
        if (authService == null) synchronized (ApiProvider.class) {
            if (authService == null) authService = retrofit(JIOTV_API_BASE).create(AuthApiService.class);
        }
        return authService;
    }

    public static <T> T createService(Class<T> serviceClass) {
        return retrofit(JIOTV_API_BASE).create(serviceClass);
    }

    private static Retrofit retrofit(String base) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AuthModels.ChannelListResponse.class, new AuthModels.ChannelListResponse.Deserializer())
                .registerTypeAdapter(Channel.class, new Channel.Deserializer())
                .setLenient().create();
        return new Retrofit.Builder().baseUrl(base).client(client())
                .addConverterFactory(GsonConverterFactory.create(gson)).build();
    }
}
