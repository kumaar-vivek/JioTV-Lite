package com.example.jiotvservice.session;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import android.util.Base64;
import com.example.jiotvservice.util.Logger;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

public final class SessionManager {
    private static final String PREF = "jiotv_secure_session";
    private static final String SSO_TOKEN = "sso_token";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String UNIQUE_ID = "unique_id";
    private static final String SUBSCRIBER_ID = "subscriber_id";
    private static final String MOBILE = "mobile";
    private static final String LB_COOKIE = "lb_cookie";
    private static final String LAST_ID = "last_channel_id";
    private static final String LAST_NUMBER = "last_channel_number";
    private static final String FAVORITE_CHANNELS = "favorite_channels";
    private static final String LAST_SSO_REFRESH = "last_sso_refresh";
    private static final String LAST_ACCESS_REFRESH = "last_access_refresh";

    private static final long JWT_LEAD_MS = 30 * 1000;
    private static final long ACCESS_FALLBACK_TTL_MS = 2 * 3600 * 1000;
    private static final long ACCESS_FALLBACK_LEAD_MS = 10 * 60 * 1000;
    private static final long SSO_FALLBACK_TTL_MS = 24 * 3600 * 1000;
    private static final long SSO_FALLBACK_LEAD_MS = 3600 * 1000;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context, PREF, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize secure session storage", e);
        }
    }

    public void saveAuthSession(String authToken, String ssoToken,
                                String refreshToken, String uniqueId,
                                String mobile) {
        long now = System.currentTimeMillis();
        prefs.edit()
                .putString(ACCESS_TOKEN, authToken)
                .putString(SSO_TOKEN, ssoToken)
                .putString(REFRESH_TOKEN, refreshToken)
                .putString(UNIQUE_ID, uniqueId)
                .putString(MOBILE, mobile)
                .putLong(LAST_ACCESS_REFRESH, now)
                .putLong(LAST_SSO_REFRESH, now)
                .apply();
    }

    public void saveSubscriberId(String subscriberId) {
        prefs.edit().putString(SUBSCRIBER_ID, subscriberId).apply();
    }

    public void saveSsoToken(String ssoToken) {
        prefs.edit()
                .putString(SSO_TOKEN, ssoToken)
                .putLong(LAST_SSO_REFRESH, System.currentTimeMillis())
                .apply();
    }

    public void saveAccessToken(String accessToken) {
        prefs.edit()
                .putString(ACCESS_TOKEN, accessToken)
                .putLong(LAST_ACCESS_REFRESH, System.currentTimeMillis())
                .apply();
    }

    public void saveLbCookie(String lbCookie) {
        prefs.edit().putString(LB_COOKIE, lbCookie).apply();
    }

    public String getAccessToken() { return prefs.getString(ACCESS_TOKEN, null); }
    public String getSsoToken() { return prefs.getString(SSO_TOKEN, null); }
    public String getRefreshToken() { return prefs.getString(REFRESH_TOKEN, null); }
    public String getUniqueId() { return prefs.getString(UNIQUE_ID, null); }
    public String getSubscriberId() { return prefs.getString(SUBSCRIBER_ID, null); }
    public String getLbCookie() { return prefs.getString(LB_COOKIE, null); }

    public boolean isLoggedIn() {
        return !isEmpty(getSsoToken()) && !isEmpty(getAccessToken()) && !isEmpty(getRefreshToken());
    }

    public boolean isAccessTokenExpired() {
        return shouldRefreshToken(getAccessToken(), LAST_ACCESS_REFRESH, 
                ACCESS_FALLBACK_TTL_MS, ACCESS_FALLBACK_LEAD_MS);
    }

    public boolean isSsoTokenExpired() {
        return shouldRefreshToken(getSsoToken(), LAST_SSO_REFRESH, 
                SSO_FALLBACK_TTL_MS, SSO_FALLBACK_LEAD_MS);
    }

    private boolean shouldRefreshToken(String token, String lastRefreshKey, long fallbackTTL, long fallbackLead) {
        if (isEmpty(token)) return true;

        Long exp = parseJWTExpiry(token);
        long now = System.currentTimeMillis();

        if (exp != null) {
            // JWT expiry is in seconds.
            return now >= (exp * 1000 - JWT_LEAD_MS);
        }

        long lastRefresh = prefs.getLong(lastRefreshKey, 0);
        if (lastRefresh == 0) return true;

        return now >= (lastRefresh + fallbackTTL - fallbackLead);
    }

    private Long parseJWTExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(payload);
            long exp = json.optLong("exp", 0);
            return exp > 0 ? exp : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void saveLastChannel(int id, int number) {
        prefs.edit().putInt(LAST_ID, id).putInt(LAST_NUMBER, number).apply();
    }
    public int getLastChannelId() { return prefs.getInt(LAST_ID, -1); }
    public int getLastChannelNumber() { return prefs.getInt(LAST_NUMBER, -1); }

    public boolean isFavoriteChannel(int channelId) {
        return getFavoriteChannelIds().contains(String.valueOf(channelId));
    }

    public boolean toggleFavoriteChannel(int channelId) {
        Set<String> ids = getFavoriteChannelIds();
        String id = String.valueOf(channelId);
        boolean nowFavorite;
        if (ids.contains(id)) {
            ids.remove(id);
            nowFavorite = false;
        } else {
            ids.add(id);
            nowFavorite = true;
        }
        prefs.edit().putStringSet(FAVORITE_CHANNELS, ids).apply();
        return nowFavorite;
    }

    public Set<String> getFavoriteChannelIds() {
        return new HashSet<>(prefs.getStringSet(FAVORITE_CHANNELS, new HashSet<>()));
    }

    public void clearFavorites() {
        prefs.edit().remove(FAVORITE_CHANNELS).apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }
}
