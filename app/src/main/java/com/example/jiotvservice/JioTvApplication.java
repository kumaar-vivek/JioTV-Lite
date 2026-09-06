package com.example.jiotvservice;

import android.app.Application;
import android.content.Context;

public class JioTvApplication extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    public static Context getContext() {
        return context;
    }
}
