package com.dragonwarrior.airplayserver;

import android.app.Application;
import android.content.Context;

import com.ffalcon.mercury.android.sdk.MercurySDK;

public class MyApplication extends Application {

    private static Context appContext;

    public static Context getAppContext() {
        return appContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext=this;

        MercurySDK.INSTANCE.init(this);
    }
}
