# BetterBatteryLife
An application to provide better standby time on Android 7 &amp; up devices.

This application allows optimized Android DOZE settings ( from XDA-developers ) to be set on non-rooted devices to greatly improve their standby times.
- With these doze settings, phone sensor motion ( putting it in your pocket ) will not accidentally wake up the phone, which causes bad battery life.

In addition to this, if the app is running and removed from the battery optimization list ( in your settings ) it will also
turn OFF the wifi when entering DOZE mode & turn it back on when leaving DOZE.


The first time you install the APK, you must ENABLE USB DEBUGGING & paste these commands ( all in once ) into your terminal with your phone attached:
  ./adb -d shell am force-stop com.MichaelAnzalone.BetterBatteryLife; \
  ./adb -d shell pm grant com.MichaelAnzalone.BetterBatteryLife android.permission.WRITE_SECURE_SETTINGS
  
  - Once this is done, you can disable usb debugging and run the application.
  - You will only ever have to run these commands again if you uninstall,re-install the APP.
    -- For this reason, when a new one comes out, upgrade the application ( instead of uninstalling & re-installing ), so you          don't have to run these commands again.

