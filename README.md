# BetterBatteryLife
An application to provide better standby time on Android 7 &amp; up devices.

This application allows optimized Android DOZE settings to be set on non-rooted devices to greatly improve their standby times.
- With these doze settings, phone sensor motion ( putting it in your pocket ) will not accidentally wake up the phone, which causes bad battery life.

In addition to this the app will also:
  1) __Turn OFF the wifi when entering DOZE mode & turn it back on when leaving__
     __DOZE. ( if it was on when the screen turned off )__
      
  2) __If bluetooth is not connected to any devices, it will alos Turn OFF the bluetooth when entering DOZE mode & turn it back on when leaving DOZE ( if it was on when the screen turned off ).__


The first time you install the APK, __you must ENABLE USB DEBUGGING & paste these commands ( all in once ) into your terminal with your phone attached:__

  adb devices
  
    Output:
      List of devices attached
      LMV600VMf50e972b	device
    - If this shows unauthorized, select yes or okay on the popups on your phone, then run this command again.
    - Replace: LMV600VMf50e972b with the value shown for your output.
  
  adb -s LMV600VMf50e972b -d shell am force-stop com.MichaelAnzalone.BetterBatteryLife
  
  adb -s LMV600VMf50e972b -d shell pm grant com.MichaelAnzalone.BetterBatteryLife android.permission.WRITE_SECURE_SETTINGS
  
  adb -s LMV600VMf50e972b -d shell dumpsys deviceidle sys-whitelist +com.MichaelAnzalone.BetterBatteryLife
  
  - On some phones like LG/Samsung that have their own battery optimiziation, you must also exclude this in the battery app from power savings &
     the app may not show up in the dumpsys whitelist, but the above commands are still necessary. 
  
  - Once this is done, you can disable usb debugging in the settings on your phone and run the application.
    
  -- You won't need to run these commands when upgrading the application, but you will if you uninstall/re-install the application.

