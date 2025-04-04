#!/bin/bash 
# Safe optimizations for any android phone.
# Will not harm battery life.

# Get the first ADB device:
readonly ADB_DEVICE=$( adb devices | awk 'NR==2{print $1}' )

echo "Optimizing the Android device: '${ADB_DEVICE}' will take a few..."

#Speed up the network + improve battery life:
 adb -s ${ADB_DEVICE} -d shell settings put system multicore_packet_scheduler 1
 adb -s ${ADB_DEVICE} -d shell settings put global network_scoring_ui_enabled 0

#Make animations twice as fast ( need developer options enabled ):
 adb -s ${ADB_DEVICE} -d shell settings put global window_animation_scale 0.5
 adb -s ${ADB_DEVICE} -d shell settings put global transition_animation_scale 0.5
 adb -s ${ADB_DEVICE} -d shell settings put global animator_duration_scale 0.5
 
# Speed up app launch time:
 adb -s ${ADB_DEVICE} -d shell settings put system rakuten_denwa 0
 adb -s ${ADB_DEVICE} -d shell settings put system send_security_reports 0
 adb -s ${ADB_DEVICE} -d shell settings put secure send_action_app_error 0
 adb -s ${ADB_DEVICE} -d shell settings put global activity_starts_logging_enabled 0
 
# Improve audio quality:
  adb -s ${ADB_DEVICE} -d shell settings put system k2hd_effect 1
  adb -s ${ADB_DEVICE} -d shell settings put system tube_amp_effect 1
  
# Improve system performance( WARNING: setprop settings reset on boot. ):
  adb -s ${ADB_DEVICE} -d shell setprop debug.force-opengl 1
  adb -s ${ADB_DEVICE} -d shell setprop debug.hwc.force_gpu_vsync 1
  
# Improve battery life:
  adb -s ${ADB_DEVICE} -d shell settings put global sem_enhanced_cpu_responsiveness 0
  adb -s ${ADB_DEVICE} -d shell settings put secure screensaver_enabled 0
  adb -s ${ADB_DEVICE} -d shell settings put secure screensaver_activate_on_sleep 0
  adb -s ${ADB_DEVICE} -d shell settings put secure screensaver_activate_on_dock 0

# Prevent OLED burn in on OLED displays ( Makes the display last longer ):
  adb -s ${ADB_DEVICE} -d shell settings put global burn_in_protection 1

# Improve touch screen responsiveness:
  adb -s ${ADB_DEVICE} -d shell settings put secure long_press_timeout 250
  adb -s ${ADB_DEVICE} -d shell settings put secure multi_press_timeout 250
  adb -s ${ADB_DEVICE} -d shell settings put secure tap_duration_threshold 0.0 
  adb -s ${ADB_DEVICE} -d shell settings put secure touch_blocking_period 0.0

# Optimize all apps:
  echo "Optimizing apps... Will take a while."
  adb -s ${ADB_DEVICE} -d shell cmd package compile -m speed-profile -a
  adb -s ${ADB_DEVICE} -d shell cmd package bg-dexopt-job

 # Clear system cache:
  echo "Clearing system cache... Will take a while."
  adb -s ${ADB_DEVICE} -d shell pm trim-caches 60000G

# Clear cache for all apps:
  echo "Clearing all app cache... Will take a while."
  adb -s ${ADB_DEVICE} -d shell cmd package list packages|cut -d":" -f2|while read package ;do adb -s ${ADB_DEVICE} -d  shell pm clear $package;done

  echo "App cache has been cleared."
  echo "The first time opening apps may be slightly slower, because not loaded from cache,"
  echo " but will get faster as you re-use those apps."

# Clear all log files ( should do this after every update to keep the device snappy ):
  adb -s ${ADB_DEVICE} -d shell logcat -b all -c

