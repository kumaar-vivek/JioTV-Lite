package com.example.jiotvservice.auth;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import com.example.jiotvservice.model.AuthModels;

public final class DeviceInfoFactory {
    private DeviceInfoFactory() {}
    public static AuthModels.DeviceInfo create(Context context) {
        String model = Build.MODEL == null ? "Android" : Build.MODEL;
        String androidId = getAndroidId(context);
        AuthModels.Platform platform = new AuthModels.Platform(model, "");
        AuthModels.DeviceInfoDetails details = new AuthModels.DeviceInfoDetails("android", platform, androidId);
        return new AuthModels.DeviceInfo(model, details);
    }

    public static String getAndroidId(Context context) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return androidId == null ? "" : androidId;
    }

    public static String getOsVersion() {
        return Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE;
    }
}
