/******************************************************************************
 Copyright (c) 2018, Indoor Robotics
 All rights reserved.

 @author Amit Moran (amit@indoor-robotics.com)
 @date Mar, 21, 2018
 *******************************************************************************/

package com.indoor_robotics.dji_android_ros_driver;

import org.ros.exception.ServiceException;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.service.ServiceResponseBuilder;
import org.ros.node.service.ServiceServer;
import org.ros.node.topic.Publisher;
import org.ros.node.topic.Subscriber;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;

public class DjiRosDriverNode implements NodeMain {

    //***************************
    // Setting up constants
    //***************************

    public static final int PERIOD = 100;
    public static final int STATUS_PERIOD = 1000;

    private static final float M_PI = 3.1459f;
    private static final java.lang.String NODE_NAME = "dji_ros_driver";
    private static final java.lang.String TAKE_OFF_CMD = "takeoff";
    private static final java.lang.String LAND_CMD = "land";
    private static final java.lang.String ROTATE_CLOCKWISE_CMD = "rotate_cw";
    private static final java.lang.String ROTATE_COUNTERCLOCKWISE_CMD = "rotate_ccw";
    private static final java.lang.String STOP_CMD = "stop";

    private static final java.lang.String BASE_TOPIC_NAME = "/flight_commands";
    private static final java.lang.String CMD_VEL_TOPIC_NAME = "/cmd_vel";
    private static final java.lang.String DONE_TOPIC_NAME = "done";
    private static final java.lang.String STATUS_TOPIC_NAME = "/dji/status";


    //***************************
    // Private Fields
    //***************************

    // Timers/Tasks:
    private Timer sendVirtualStickDataTimer = null;
    private SendVirtualStickDataTask sendVirtualStickDataTask = null;
    private Timer droneStatusTimer = null;
    private DroneStatusTask droneStatusTask = null;

    // Movement command values
    private float pitch = 0f;
    private float roll = 0f;
    private float yaw = 0f;
    private float throttle = 0f;

    private boolean initialized = false;
    private float batteryLevelAvg = 0.0f;
    private boolean isConnected = false;
    private boolean isCallbackSetup = false;
    private boolean areMotorsOn = false;
    private boolean isFlying = false;
    private float altitude = 0.0f;
    private double latitude = 0.0f;
    private double longitude = 0.0f;
    private boolean landConfirmNeeded = false;

    // Topics and services, publishers, types
    private java.lang.String commandsTopicName = BASE_TOPIC_NAME;
    private java.lang.String commandsMessageType = std_msgs.String._TYPE;
    private java.lang.String commandsResTopicName = BASE_TOPIC_NAME + "/" + DONE_TOPIC_NAME;

    private java.lang.String cmdvelTopicName = CMD_VEL_TOPIC_NAME;
    private java.lang.String cmdvelMessageType = geometry_msgs.Twist._TYPE;

    private java.lang.String djiStatusTopicName = STATUS_TOPIC_NAME;
    private java.lang.String djiStatusMessageType = std_msgs.String._TYPE;

    private Publisher<std_msgs.String> pubDjiStatus;
    private Publisher<std_msgs.Empty> pubResult;
    private ServiceServer<std_srvs.EmptyRequest, std_srvs.EmptyResponse> serverTakeOff;
    private ServiceServer<std_srvs.EmptyRequest, std_srvs.EmptyResponse> serverLand;
    private ServiceServer<std_srvs.EmptyRequest, std_srvs.EmptyResponse> serverRotate;
    private ServiceServer<std_srvs.EmptyRequest, std_srvs.EmptyResponse> serverStop;

    //***************************
    // Public Methods
    //***************************

    public java.lang.String getCommandsTopicName() {
        return this.commandsTopicName;
    }

    public java.lang.String getCommandsMessageType() {
        return this.commandsMessageType;
    }

    public void setCommandsTopicName(java.lang.String topicName) {
        this.commandsTopicName = topicName;
    }

    public void setCommandsMessageType(java.lang.String messageType) {
        this.commandsMessageType = messageType;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of(NODE_NAME);
    }

    public void StopVirtualSticks() {
        final FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
        initializeIfNeeded(flightController);
        flightController.setVirtualStickModeEnabled(false, null);
    }


    public void StartVirtualSticks() {
        final FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
        initializeIfNeeded(flightController);
        flightController.setVirtualStickModeEnabled(false, null);
        flightController.setVirtualStickModeEnabled(true, null);
    }

    //***************************
    // Public callbacks
    //***************************

    @Override
    public void onStart(ConnectedNode connectedNode) {

        //Setting up publishers
        pubDjiStatus = connectedNode.newPublisher(djiStatusTopicName, djiStatusMessageType);
        pubResult = connectedNode.newPublisher(commandsResTopicName, std_msgs.Empty._TYPE);

        serverTakeOff = connectedNode.newServiceServer(
                commandsTopicName + "/" + TAKE_OFF_CMD, std_srvs.Empty._TYPE, new ServiceResponseBuilder<std_srvs.EmptyRequest, std_srvs.EmptyResponse>() {
                    @Override
                    public void build(std_srvs.EmptyRequest empty, std_srvs.EmptyResponse empty2) throws ServiceException {
                        final FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
                        initializeIfNeeded(flightController);
                        flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                pubResult.publish(pubResult.newMessage());
                                StartVirtualSticks();
                            }
                        });
                    }
                });

        serverLand = connectedNode.newServiceServer(
                commandsTopicName + "/" + LAND_CMD, std_srvs.Empty._TYPE, new ServiceResponseBuilder<std_srvs.EmptyRequest, std_srvs.EmptyResponse>() {
                    @Override
                    public void build(std_srvs.EmptyRequest empty, std_srvs.EmptyResponse empty2) throws ServiceException {
                        final FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
                        initializeIfNeeded(flightController);

                        flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                pubResult.publish(pubResult.newMessage());
                            }
                        });
                    }
                });

        serverRotate = connectedNode.newServiceServer(
                commandsTopicName + "/" + ROTATE_CLOCKWISE_CMD, std_srvs.Empty._TYPE, new ServiceResponseBuilder<std_srvs.EmptyRequest, std_srvs.EmptyResponse>() {
                    @Override
                    public void build(std_srvs.EmptyRequest empty, std_srvs.EmptyResponse empty2) throws ServiceException {
                        final FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
                        initializeIfNeeded(flightController);
                        if (isFlying) {
                            yaw = -20f;
                        }
                    }
                });

        serverStop = connectedNode.newServiceServer(
                commandsTopicName + "/" + STOP_CMD, std_srvs.Empty._TYPE, new ServiceResponseBuilder<std_srvs.EmptyRequest, std_srvs.EmptyResponse>() {
                    @Override
                    public void build(std_srvs.EmptyRequest empty, std_srvs.EmptyResponse empty2) throws ServiceException {
                        final FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
                        initializeIfNeeded(flightController);

                        //Setting all control parameters to 0 and disable virtual sticks

                        pitch = 0f;
                        roll = 0f;
                        yaw = 0f;
                        throttle = 0f;
                        flightController.setVirtualStickModeEnabled(false, null);

                    }
                });

        //Setting up listeners
        Subscriber subCmdvel = connectedNode.newSubscriber(this.cmdvelTopicName, this.cmdvelMessageType);

        subCmdvel.addMessageListener(new MessageListener() {
            @Override
            public void onNewMessage(Object o) {

                // save commands in global fields which will be used in for the virtual sticks

                geometry_msgs.Twist message = (geometry_msgs.Twist) o;
                throttle = (float) message.getLinear().getZ(); // Movement along the Z axis
                pitch = (float) message.getLinear().getY(); // Movement along the y axis
                roll = (float) message.getLinear().getX(); // Movement along the x axis
                yaw = ((float) message.getAngular().getZ() * 180) / M_PI; // Convert to degrees. ROS works with radians...

            }
        });

        //Running threads
        runVirtualStickThread();
        runDroneStatusThread();
    }

    @Override
    public void onShutdown(Node node) {
        if (null != sendVirtualStickDataTimer) {
            sendVirtualStickDataTimer.cancel();
            sendVirtualStickDataTimer.purge();
            sendVirtualStickDataTimer = null;
            sendVirtualStickDataTask = null;
        }

        if (null != droneStatusTimer) {
            droneStatusTimer.cancel();
            droneStatusTimer.purge();
            droneStatusTimer = null;
            droneStatusTask = null;
        }
    }

    @Override
    public void onShutdownComplete(Node node) {

    }

    @Override
    public void onError(Node node, Throwable throwable) {

    }


    //***************************
    // Private Methods
    //***************************

    private void runVirtualStickThread() {
        if (null == sendVirtualStickDataTimer) {
            sendVirtualStickDataTask = new SendVirtualStickDataTask();
            sendVirtualStickDataTimer = new Timer();
            sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 0, PERIOD);
        }
    }

    private void runDroneStatusThread() {
        if (null == droneStatusTimer) {
            droneStatusTask = new DroneStatusTask();
            droneStatusTimer = new Timer();
            droneStatusTimer.schedule(droneStatusTask, 0, STATUS_PERIOD);
        }
    }

    private void initializeIfNeeded(FlightController flightController) {

        if (!initialized) {

            flightController.setVirtualStickModeEnabled(true, null);
            flightController.setNoviceModeEnabled(false, null);
            flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            flightController.getFlightAssistant().setCollisionAvoidanceEnabled(false, null);
            flightController.getFlightAssistant().setActiveObstacleAvoidanceEnabled(false, null);
            flightController.setFlightOrientationMode(FlightOrientationMode.AIRCRAFT_HEADING, null);

            initialized = true;
        }
    }

    //***************************
    // Private nested classes
    //***************************

    /**
     * A thread running a loop sending sticks inputs to the Drone
     */
    private class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            if (BaseDJIApplication.getProductInstance() != null) {
                FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
                initializeIfNeeded(flightController);

                //use the global fields and send as virtual sticks params.

                flightController.sendVirtualStickFlightControlData(new FlightControlData(pitch,
                                roll,
                                yaw,
                                throttle),
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });
            }
        }
    }

    /**
     * A thread running status checks from the drone and send as a topic to ROS
     */
    private class DroneStatusTask extends TimerTask {

        @Override
        public void run() {
            if (BaseDJIApplication.getProductInstance() != null) {
                isConnected = BaseDJIApplication.getProductInstance().isConnected();

                if (!isCallbackSetup) {
                    BaseDJIApplication.getProductInstance().getBattery().setStateCallback(new BatteryState.Callback() {
                        @Override
                        public void onUpdate(BatteryState batteryState) {
                            batteryLevelAvg = batteryState.getChargeRemainingInPercent();
                        }
                    });


                    ((Aircraft) BaseDJIApplication.getProductInstance()).getFlightController().setStateCallback(new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(FlightControllerState flightControllerState) {
                            areMotorsOn = flightControllerState.areMotorsOn();
                            isFlying = flightControllerState.isFlying();
                            altitude = flightControllerState.getAircraftLocation().getAltitude();
                            latitude = flightControllerState.getAircraftLocation().getLatitude();
                            longitude = flightControllerState.getAircraftLocation().getLongitude();
                            landConfirmNeeded = flightControllerState.isLandingConfirmationNeeded();

                            if (landConfirmNeeded) {
                                FlightController flightController = ((Aircraft) (BaseDJIApplication.getProductInstance())).getFlightController();
                                flightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                        pubResult.publish(pubResult.newMessage());
                                    }
                                });
                            }
                        }
                    });
                }
            }

            std_msgs.String msg = pubDjiStatus.newMessage();
            msg.setData("battery=" + batteryLevelAvg + ";isConnected=" + isConnected + ";areMotorsOn=" + areMotorsOn + ";isFlying=" + isFlying + ";altitude=" + altitude);
            pubDjiStatus.publish(msg);
        }
    }

}
