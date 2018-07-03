/******************************************************************************
 Copyright (c) 2018, Indoor Robotics
 All rights reserved.

 @author Amit Moran (amit@indoor-robotics.com)
 @date Mar, 21, 2018
 *******************************************************************************/

package com.indoor_robotics.dji_android_ros_driver;

import android.app.Application;
import android.content.Context;
import com.secneo.sdk.Helper;

public class MApplication extends Application {
    private BaseDJIApplication baseApplication;
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this);
        if (baseApplication == null) {
            baseApplication = new BaseDJIApplication();
            baseApplication.setContext(this);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        baseApplication.onCreate();
    }
}