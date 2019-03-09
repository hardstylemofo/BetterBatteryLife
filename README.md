# BetterBatteryLife
An application to provide better standby time on Android 7 &amp; up devices.

This application allows optimized Android DOZE settings ( from XDA-developers ) to be set on non-rooted devices to greatly improve their standby times.
- With these doze settings, phone sensor motion ( putting it in your pocket ) will not accidentally wake up the phone, which causes bad battery life.

In addition to this the app will also:
  1) __Turn OFF the wifi when entering DOZE mode & turn it back on when leaving__
     __DOZE. ( if it was on when the screen turned off )__
      
  2) __If bluetooth is not connect to any devices, it will Turn OFF the bluetooth when entering DOZE mode & turn it back on when leaving DOZE ( if it was on when the screen turned off ).__


The first time you install the APK, __you must ENABLE USB DEBUGGING & paste these commands ( all in once ) into your terminal with your phone attached:__
  ./adb -d shell am force-stop com.MichaelAnzalone.BetterBatteryLife; \
  ./adb -d shell pm grant com.MichaelAnzalone.BetterBatteryLife android.permission.WRITE_SECURE_SETTINGS
  
  - Once this is done, you can disable usb debugging and run the application.
  -- You won't need to run these commands when upgrading the application, but you will if you uninstall/re-install the application.

