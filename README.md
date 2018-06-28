# dji_android_ros_driver
ROS driver for DJI drones using DJI Mobile SDK

ROS wiki page: http://wiki.ros.org/dji_android_ros_driver

== Overview ==
This package is an android mobile application for using DJI advanced consumer drones with the ROS system.

The DJI driver is a mobile (android) application. It uses the Android (Java) implementation of ROS and the DJI Mobile SDK to communicate with a ROS machine and DJI drone respectfully. It exposes standard interfaces for command velocities (twist commands) and some specific API (services) for tasks like takeoff, landing etc. The mobile device itself should be connected to the remote control. 

Having the remote control in the loop is actually very important. It provides extra information on its display and most importantly, a safety mechanism to take manual control over the drone (it saved our lives several times when the SW or sensors failed…) 

