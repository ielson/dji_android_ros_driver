/******************************************************************************
 Copyright (c) 2018, Indoor Robotics
 All rights reserved.

 @author Amit Moran (amit@indoor-robotics.com)
 @date Mar, 21, 2018
 *******************************************************************************/

package com.indoor_robotics.dji_android_ros_driver;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import org.ros.android.MessageCallable;
import org.ros.android.RosActivity;
import org.ros.android.view.RosTextView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.util.Timer;
import java.util.TimerTask;

public class MainROSActivity extends RosActivity {

    private TextView textView;
    private Switch connectedSwitch,connectedROSSwitch;
    private ProgressBar connectedProgress;

    private DjiRosDriverNode rosDriver = new DjiRosDriverNode();
    private IsRosConnectedNode isRosConnectedNode = new IsRosConnectedNode();

    private Timer checkConnectionToDroneTimer = null;
    private CheckConnectionToDroneTask checkConnectionToDroneTask;
    private Button btn;

    private boolean isDroneConnected = false;

    public MainROSActivity() {
        super("DJI-Ros Driver Activity", "DJI-Ros Driver Activity");

        // If you know the IP/Port of the ROS Master, you can set it as follows and avoid having the Master Chooser activity:

        //super("DJI-Ros Driver Activity", "DJI-Ros Driver Activity", URI.create("http://10.42.0.1:11311");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }
        setContentView(R.layout.dji_ros_driver_activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

        //setting the debug view:
        textView = (TextView) findViewById(R.id.textDebug);

        //Setting up Guid elemends for user feedback
        connectedSwitch = (Switch)findViewById(R.id.switch_dron_conn);
        connectedROSSwitch = (Switch)findViewById(R.id.switch_ros_conn);
        connectedProgress = (ProgressBar) findViewById(R.id.connected_progressBar);

        checkConnectionToDroneTask = new CheckConnectionToDroneTask();
        checkConnectionToDroneTimer = new Timer();
        checkConnectionToDroneTimer.schedule( checkConnectionToDroneTask, 0, 500);

        // in order to start the ROS control, user must press on this button. From some reason it is not working straight away
        btn = (Button)findViewById(R.id.button_start_vsticks);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rosDriver.StartVirtualSticks();

            }
        });

    }

    /**
     * A thread running a contant check that the connection to the Drone and to ROS is available
     */
    private class CheckConnectionToDroneTask extends TimerTask {

        @Override
        public void run() {
            final boolean isDroneConnected = BaseDJIApplication.getProductInstance() != null ? BaseDJIApplication.getProductInstance().isConnected() : false;
            final boolean isROSConnected = isRosConnectedNode.IsConnected();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectedSwitch.setChecked(isDroneConnected);
                    connectedROSSwitch.setChecked(isROSConnected);
                    btn.setEnabled(isDroneConnected);
                    connectedProgress.setVisibility(isDroneConnected && isROSConnected ? View.GONE : View.VISIBLE);
                }
            });
        }
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {

        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(getRosHostname());
        nodeConfiguration.setMasterUri(getMasterUri());

        //Running nodes
        nodeMainExecutor.execute(isRosConnectedNode, nodeConfiguration);
        nodeMainExecutor.execute(rosDriver, nodeConfiguration);

    }
}