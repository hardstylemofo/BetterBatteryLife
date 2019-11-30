package com.MichaelAnzalone.betterbatterylife

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.content.BroadcastReceiver
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.Nullable
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Process
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs



class BetterBatteryLifeService : Service() {

    ////////////// User Settings ///////////////
    companion object {
        val ACTION_APPLY_SETTINGS: String = "APPLY_SETTINGS"
        val ACTION_APPLY_SETTINGS_COMPLETED: String = "APPLY_SETTINGS_COMPLETED"
        val ACTION_GET_SETTINGS: String = "GET_SETTINGS"
        val ACTION_GET_SETTINGS_COMPLETED: String = "GET_SETTINGS_COMPLETED"

        val SETTING_DISABLE_WIFI_DURING_DEEP_SLEEP: String = "DISABLE_WIFI_DURING_DEEP_SLEEP"
        val SETTING_DISABLE_BLUETOOTH_DURING_DEEP_SLEEP: String = "DISABLE_BLUETOOTH_DURING_DEEP_SLEEP"
        val SETTING_GET_EXTRA_INFO:String = "GET_EXTRA_INFO"
        val PERSISTANT_NOTIFICATION_ID: Int = 101
    }
    @Volatile private var disableWifiDuringDeepSleep: Boolean = true
    @Volatile private var disableBluetoothDuringDeepSleep: Boolean = true
    ////////////////////////////////////////////

    /** indicates whether onRebind should be used  */
    var mAllowRebind: Boolean = false

    var dozeReceiver: BroadcastReceiver? = null
    var screenReceiver: BroadcastReceiver? = null
    var bluetoothReceiver: BroadcastReceiver? = null
    var wifiReceiver: BroadcastReceiver? = null
    var applicationReceiver: BroadcastReceiver? = null
    var powerConnectionReceiver: BroadcastReceiver? = null

    // Notfication info:
    var notification : Notification? = null
    var notificationBuilder : NotificationCompat.Builder? = null

    @Volatile var turnBluetoothBackOn = false
    @Volatile var turnWifiBackOn = false
    @Volatile var turnBatterySaverOff = true // Battery saver would normally be off.

    var localBroadcastManager : LocalBroadcastManager? = null

    @Volatile private var lastWifiTurnOffTime: String = ""
    @Volatile private var lastDozeStartTime: String = ""
    @Volatile private var serviceStartDateTimeMessage: String = ""
    @Volatile private var unplugTimeSeconds: Long = 0
    @Volatile private var unplugBatteryPercentage: Float = 0f
    @Volatile private var screenOffBatteryPercentageNumPoints: Float = 1f // Start on the 1st data point.

    @Volatile private var lastUnplugTime: String = ""
    @Volatile private var screenOffBatteryDrainPerHour: Float = 0f // How many % the battery dropped during the last screen off.
    @Volatile private var screenOffBatteryDrainPerHourSum: Float = 0f
    @Volatile private var screenOffTimeNanoSeconds: Long = 0
    @Volatile private var deepDozeCount: ULong = 1UL // How many deep dozes since screen off.
    @Volatile private var sensorControlError: String = ""
    @Volatile private var batterySaverError: String = ""

    private var serviceRunning: Boolean = false

    fun CheckDOZEPermissions(): Boolean {

        // Must be safe
        var requiredPermission = "android.permission.WRITE_SECURE_SETTINGS"
        var checkVal = checkSelfPermission(requiredPermission)

        if (checkVal == PackageManager.PERMISSION_DENIED) {
            return false
        }

        return true
    }

    /** Called when the service is being created.  */
    override fun onCreate() {
        super.onCreate()

        // Listen for DOZE state changes:
        RegisterDozeState()

        // Listen for screen ON events:
        RegisterScreenState()

        // Listen for events from our main application:
        RegisterApplicationReceiver()

        // Listen for device power plugged/unplugged events:
        RegisterPowerConnectionReceiver()
//
//        ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER)
//
//        mIDeviceIdleController = IDeviceIdleController.Stub.asInterface(
//            ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER)
//        )

        // Listen for bluetooth events:
        // Not needed for now.
        //RegisterBluetoothState()

        // Listen for wifi events:
        // Not needed for now.
        //RegisterWifiState()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        // Is this the first time starting the service?:
        if(!serviceRunning) {

            // Yes, indicate that we are running:
            serviceRunning = true

            // Now, handle the first start:
            super.onStartCommand(intent, flags, startId)

            // Enable the persistent notification required, to keep this foreground
            //  service running forever:
            customNotification("Service Info", BetterBatteryLifeService.PERSISTANT_NOTIFICATION_ID)

            // Save the states of WIFI and bluetooth, just in case the user changed them:
            turnWifiBackOn = isWifiOn()
            turnBluetoothBackOn = isBluetoothOn()
            turnBatterySaverOff = !inBatterySaverMode()

            if (CheckDOZEPermissions()) {
                SetupOptimizedDozeParams(true)
            }
        }

        return Service.START_STICKY
    }


    fun SetupOptimizedDozeParams(showToastNotifications: Boolean, stayInDozeUntilUnlocked: Boolean = false) : Boolean {
        var result = false

        /// Greenify's aggressive doze settings:
        /*
            light_after_inactive_to=+3m0s0ms
            light_pre_idle_to=+3m0s0ms
            light_idle_to=+5m0s0ms
            light_idle_factor=2.0
            light_max_idle_to=+15m0s0ms
            light_idle_maintenance_min_budget=+1m0s0ms
            light_idle_maintenance_max_budget=+5m0s0ms
            min_light_maintenance_time=+5s0ms
            min_deep_maintenance_time=+30s0ms
            inactive_to=+10s0ms
            sensing_to=+4m0s0ms
            locating_to=+30s0ms
            location_accuracy=20.0m
            motion_inactive_to=+10m0s0ms
            idle_after_inactive_to=+30m0s0ms
            idle_pending_to=+5m0s0ms
            max_idle_pending_to=+10m0s0ms
            idle_pending_factor=2.0
            idle_to=+1h0m0s0ms
            max_idle_to=+6h0m0s0ms
            idle_factor=2.0
            min_time_to_alarm=+1h0m0s0ms
            max_temp_app_whitelist_duration=+5m0s0ms
            mms_temp_app_whitelist_duration=+1m0s0ms
            sms_temp_app_whitelist_duration=+20s0ms
            notification_whitelist_duration=+30s0ms
            wait_for_unlock=false
         */

        var optimizedConfig =
               "light_after_inactive_to=180000," +
               "light_pre_idle_to=0," +
               "light_idle_to=300000," +
               "light_idle_factor=2.0," +
               "light_max_idle_to=300000," + // 5 minutes
               "light_idle_maintenance_min_budget=60000," +
               "light_idle_maintenance_max_budget=300000," +
               "min_light_maintenance_time=5000," +
               "min_deep_maintenance_time=30000," +
               "inactive_to=10000," +
               "sensing_to=0," +
               "locating_to=0," +
               "location_accuracy=20.0," +
               "motion_inactive_to=0," +
               "idle_after_inactive_to=0," +
               "idle_pending_to=60000," +
               "max_idle_pending_to=120000," +
               "idle_pending_factor=1.0," +
               //"idle_to=3600000," +        // After screen off, stay in Deep sleep for 1 hours. <--- First deep sleep duration after screen off.
               "idle_to=7200000," +        // After screen off, stay in Deep sleep for 2 hours. <--- First deep sleep duration after screen off.
               "max_idle_to=36000000," +   // Stay in deep sleep for 10 hours for the following deep sleeps. <--- 2nd ( or more ) deep sleep durations.
               "idle_factor=2.0," +
               "min_time_to_alarm=3600000," // Every 60 minutes
               "max_temp_app_whitelist_duration=60000," +
               "mms_temp_app_whitelist_duration=30000," +
              "sms_temp_app_whitelist_duration=20000," +
              "notification_whitelist_duration=30000,"

        // Should we stay in doze until the user unlocks the phone?:
        if(stayInDozeUntilUnlocked)
        {
            // Yes:
            optimizedConfig +=  "wait_for_unlock=true"
        }
        else
        {
            // No, come out of doze, even if the phone is not unlocked.
            optimizedConfig +=  "wait_for_unlock=false"
        }

//        val optimizedConfig = "inactive_to=10000," + // After 10 seconds, enter DEEP DOZE.
//                "sensing_to=0," +                    // Ignore sensors when entering DEEP/Light DOZE.
//                "light_after_inactive_to=1200000," + // After 20 minutes, enter light DOZE.
//                "locating_to=0," +                   // Ignore location when entering DEEP/Light DOZE.
//                "location_accuracy=20.0," +          // Not used if: "locating_to=0" is set.
//                "light_pre_idle_to=0,"    +          // Don't wait before entering light idle mode.
//                "motion_inactive_to=0," +
//                "idle_after_inactive_to=0," +
//                "idle_pending_to=30000," +
//                "max_idle_pending_to=120000," +
//                "idle_pending_factor=2.0," +
//                "idle_to=1000000," +
//                "max_idle_to=86400000," +
//                "idle_factor=2.0," +
//                "min_time_to_alarm=600000," + // 10 minutes.
//                "max_temp_app_whitelist_duration=10000," +
//                "mms_temp_app_whitelist_duration=10000," +
//                "sms_temp_app_whitelist_duration=10000," +
//                "light_idle_to=120000," +
//                "light_idle_maintenance_min_budget=60000," +
//                "light_idle_maintenance_max_budget=120000," +
//                "wait_for_unlock=false" // If set to true, will delay notifications on On Screen Displays...


        try {
                val config = Settings.Global.getString(
                    this.contentResolver,
                    "device_idle_constants"
                )

            // Are the settings different?:
                if (config != optimizedConfig) {
                    // Yes, update them:
                    Settings.Global.putString(
                        this.contentResolver,
                        "device_idle_constants",
                        optimizedConfig
                    )

                }
                else
                {
                    // Nothing to do:
                    return true
                }

        } catch (e: Exception) {

            if(showToastNotifications)
            {
                // your code
                var toast = Toast.makeText(
                    this, e.message,
                    Toast.LENGTH_LONG
                )

                toast.setGravity(
                    Gravity.CENTER,
                    0,
                    0
                )

                toast.show()
            }

            return result
        }

        val config2 = Settings.Global.getString(
            this.contentResolver,
            "device_idle_constants"
        )

        if (config2 == optimizedConfig) {

            if(showToastNotifications) {
                // your code
                var toast = Toast.makeText(
                    this, "Optimized Standby Active!",
                    Toast.LENGTH_LONG
                )

                toast.setGravity(
                    Gravity.CENTER,
                    0,
                    0
                )

                toast.show()
            }

            // Success!
            result = true

        } else {

            if(showToastNotifications) {
                var toast = Toast.makeText(
                    this, "BetterBatteryLife Could NOT Update Settings!",
                    Toast.LENGTH_LONG
                )

                toast.setGravity(
                    Gravity.CENTER,
                    0,
                    0
                )

                toast.show()
            }
        }

        return result
    }

    fun getBatteryPercentage() : Float{
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        val batteryStatus:Intent? = applicationContext.registerReceiver(null, ifilter)

        var level : Int = -1
        var scale :Int = -1
        var percentage: Float = 0f

        if(batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, level)
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, scale)
            val batteryPct : Float = ( (level as Number).toFloat() / (scale as Number).toFloat() )
            percentage = batteryPct * 100f
        }

        Log.d("Battery percentage =", percentage.toString() )

        return percentage
    }
    private var batteryPercentageScreenOff : Float = 0f

    // Get's called when the device's screen turn's off:
    fun onScreenOff(intent: Intent) {

        // Save the states of WIFI and bluetooth, just in case the user changed them:
        turnWifiBackOn = isWifiOn()
        turnBluetoothBackOn = isBluetoothOn()
        turnBatterySaverOff = !inBatterySaverMode()

        // Save the battery percentage when the screen was turned off:
        batteryPercentageScreenOff = getBatteryPercentage()

        // Save the current time in seconds:
        screenOffTimeNanoSeconds = SystemClock.elapsedRealtimeNanos()

        // Force enter deep sleep:
        try {
            // TODO: Check if phone is !pluggedIn.
            if( CheckDOZEPermissions() ) {
                SetupOptimizedDozeParams(false)
            }

        } catch (e: Exception) {

                // your code
                var toast = Toast.makeText(
                    this, e.message,
                    Toast.LENGTH_LONG
                )

                toast.setGravity(
                    Gravity.CENTER,
                    0,
                    0
                )

                toast.show()
        }
    }

    // Get's called when the device's screen turn's on ( could be on the lock screen ):
    fun onScreenON(intent: Intent) {
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Should we turn off battery saver mode?:
//        if(checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED && inBatterySaverMode() && turnBatterySaverOff )
//        {
//            // Yes:, try to turn off the battery saver:
//            if( !Settings.Global.putInt(this.contentResolver, "low_power", 0) )
//            {
//                // Could not disable the battery saver!:
//                batterySaverError = "ERROR: Could not disable the battery saver!"
//            }
//        }

        if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            // No, turn ON wifi:

            if( turnWifiBackOn && !inAirplaneMode() ) { // Can't turn on/off wifi in airplane mode due to permissions.
                var turnedONWifi = wifi.setWifiEnabled(true)

                if (!turnedONWifi) {
                    Log.d(
                        "DEBUG/SCREEN/WIFI_COULD_NOT_BE_ON", "intent action=" + intent.action
                    )
                } else {

                    Log.d(
                        "DEBUG/SCREEN/WIFI_ON", "intent action=" + intent.action
                    )
                }
            }
        }

        // Can we handle turning on/off bluetooth?:
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {

            // Are there no devices connected to bluetooth?:
            if (deviceHasBluetooth() && turnBluetoothBackOn && !isBluetoothHeadsetConnected() ) {

                // No, we can turn it ON:
                val turnedONBluetooth = enableBluetooth(true)

                // Could we not turn off bluetooth?:
                if (!turnedONBluetooth) {
                    // No, log the error:
                    Log.d(
                        "DEBUG/DOZE/BLUETOOTH_COULD_NOT_BE_ON", "intent action=" + intent.action
                    )
                } else {
                    Log.d(
                        "DEBUG/DOZE/BLUETOOTH_ON", "intent action=" + intent.action
                    )
                }
            }
        }

        // Re-enable sensor access in OREO:
//        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED) {
//            val shell = Shell()
//
//            // Re-enable sensor access:
//            var result = shell.RunCommand("dumpsys sensorservice enable")
//
//            // Should be an empty line on success:
//            if (result.isNullOrEmpty()) {
//                result = shell.RunCommand("dumpsys sensorservice")
//
//                if (!result.contains("Mode : NORMAL")) {
//                    // Could not re-enable sensors!!
//                    sensorControlError = "ERROR: Could not re-enable sensors!"
//                }
//            }
//        }

        // Show the "Unplugged on:..." message:
        var notificationContent: String = "Unplugged on: " + lastUnplugTime

        // No unplugged time recorded?:
        if(lastUnplugTime.isNullOrEmpty())
        {
            // No, so show the running since.. message:
            notificationContent = serviceStartDateTimeMessage
        }

        // Get the battery percentage loss, from the last screen turn off until now: ( screen-off battery drain ):
        val screenOffBatteryDrain = batteryPercentageScreenOff - getBatteryPercentage()

        // Was there any battery drain, during the last screen off?:
        // NOTE: This will be < 0 if the device is charging.
        if(screenOffBatteryDrain > 0f) {
//
//            val elapsedTimeNanoSeconds = ( SystemClock.elapsedRealtimeNanos() - screenOffTimeNanoSeconds )
//
//            // Yes, get the duration between: the screen off time in seconds and the current time in seconds:
//            val elapsedSecondsSinceScreenOff = elapsedTimeNanoSeconds / 1000000000.0
//
//            // Now convert this duration into hours ( So we can show something like: 0.4%/hr ):
//            val elapsedHoursSinceScreenOff: Double = elapsedSecondsSinceScreenOff / 3600.0    // 3600 seconds in an hour.
//
//            // Figure out the instantaneous screen off battery drain per hour:
            //screenOffBatteryDrainPerHour = ( screenOffBatteryDrain / elapsedHoursSinceScreenOff )
            screenOffBatteryDrainPerHour = screenOffBatteryDrain * 1f

                    // Calculate & update the sum off the total screen off battery drain per hour data points:
            //screenOffBatteryDrainPerHourSum += screenOffBatteryDrainPerHour
            screenOffBatteryDrainPerHourSum += screenOffBatteryDrain

//            // Calculate & update the average off the screen off battery drain per hour:
//            var screenOffBatteryDrainPerHourAvg: Double = screenOffBatteryDrainPerHourSum / screenOffBatteryPercentageNumPoints
//
//            // Count how many data points that we have averaged:
//            ++screenOffBatteryPercentageNumPoints

            // Make sure there was some battery drain ( user didn't turn off/on phone real quick ).
            if (screenOffBatteryDrainPerHourSum > 0f) {
                // Update the persistent notification:
                notification = updateNotificationContents(
                    String.format("%.2f%% total screen off lost since unplugged.", screenOffBatteryDrainPerHourSum),
                    notificationContent
                )

                // Also let the GUI know ( if it's running ):
                onActionGetSettings()

            } else // No battery drain/charging, so show something else:
            {
                notification = updateNotificationContents(
                    "Service Info",
                    notificationContent
                )
            }
        }
        else // No change, reset the screen off battery drain per hour for the GUI:
        {
            screenOffBatteryDrainPerHour = 0f

            // Also let the GUI know ( if it's running ):
            onActionGetSettings()
        }

        deepDozeCount = 1UL
    }

    fun RegisterScreenState() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if( intent.action == Intent.ACTION_SCREEN_ON ) {
                    onScreenON(intent)
                }
                else {
                    onScreenOff(intent)
                }
            }
        }

        registerReceiver(screenReceiver, filter)
    }

    fun RegisterDozeState() {
        val filter = IntentFilter()
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)

        dozeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val quietHours = onDozeWifiEvent(intent)

                // If we have access to the doze settings, see if they have been changed by other service
                //  like the play services, ( which is known to revert them back every once and a while grrrrr. ) and
                //  change them if necessary.
                if (CheckDOZEPermissions()) {
                    // Keep in doze mode ( until user unlocks it ) only if we are in quiet hours:
                    SetupOptimizedDozeParams(false, quietHours)
                }
            }
        }

        registerReceiver(dozeReceiver, filter)
    }

    fun RegisterBluetoothState() {
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )

                    // If the user turned on bluetooth, we will turn it back on after we exit DOZE deep sleep:
                    turnBluetoothBackOn = ( state == BluetoothAdapter.STATE_ON ||
                                            state == BluetoothAdapter.STATE_TURNING_ON)
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)
    }

    fun RegisterWifiState() {

        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                when (action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        turnWifiBackOn = isWifiOn()

                        //...else WIFI_STATE_DISABLED, WIFI_STATE_DISABLING, WIFI_STATE_ENABLING
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(wifiReceiver, filter)
    }

    fun isWifiOn() : Boolean {
        val wifi: WifiManager? = getSystemService(Context.WIFI_SERVICE) as WifiManager

        return ( wifi != null && ( wifi.wifiState == WifiManager.WIFI_STATE_ENABLED  ||
                                   wifi.wifiState == WifiManager.WIFI_STATE_ENABLING ) )
    }

    // Returns true, if we are in quiethours:
    fun onDozeWifiEvent(intent: Intent) : Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Will be true, if we are in DEEP sleep ( IDLE ) mode:
        val inDeepDoze = pm.isDeviceIdleMode

        val localCalendar = Calendar.getInstance(TimeZone.getDefault())

        val currentHour = localCalendar.get(Calendar.HOUR_OF_DAY) // HOUR_OF_DAY = 0 to 23.

        // Quiet Hours: 11PM( 23:00 ) to: 7AM ( 07:00 ):
        // - We will leave WIFI/Bluetooth OFF during quiet hours, if they were turned off.
        val quietHours: Boolean = (currentHour >= 23 && currentHour <= 7 )

        //val currentDay = localCalendar.get(Calendar.DATE)
//        val currentDayOfWeek = localCalendar.get(Calendar.DAY_OF_WEEK)
//        val currentDayOfMonth = localCalendar.get(Calendar.DAY_OF_MONTH)
//        val CurrentDayOfYear = localCalendar.get(Calendar.DAY_OF_YEAR)

        // Only turn off or on wifi, ( leave it on if it's on ) if it's the second deep doze cycle:
        // NOTE: in the first hour, the user may be turning off or on their phone, making wifi go off and on can cause
        //          more battery drain.
        // NOTE: screenOffTimeSeconds will be zero if the user has not turned on/off the screen yet, ( unplugging from charger and walking away )
        //          so we will allow turning off wifi in all deep doze cycles, as the phone is stationary.
        var allowWifiControl = ( screenOffTimeNanoSeconds == 0L || ( inDeepDoze && deepDozeCount >= 2UL ) ) // Allow wifi turn off, if screen off was more than an 1 hour.

        // Will be true if we will allow bluetooth to be turned off/on:
        var allowBlueToothControl = allowWifiControl // Allow bluetooth turn off after an hour.

        // When to allow the battery saver to be turned off and on, only if it was disabled on screen off.
        // - If the user had battery saver on, we will just leave it on.
//        var allowBatterySaverControl = quietHours || ( allowWifiControl && turnBatterySaverOff ) // turnBatterySaverOff is based off of the battery saver setting at screen OFF.

        // disable this for now due to problems on samsung devices:
        var allowBatterySaverControl = false

        // For WIFI, don't allow wifi control if we are in airplane mode ( as it's not allowed ):
        allowWifiControl = ( allowWifiControl && !inAirplaneMode() )

        if (inDeepDoze) // Entered DOZE?:
        {
            // Save the current Data:
            lastDozeStartTime = SimpleDateFormat("MMM d yyyy, hh:mm:ss a").format(Calendar.getInstance().time)

            // Count how many deep dozes we did since screen OFF:
            if(deepDozeCount < 2UL) { // Stop counting at 2 deep dozes.
                ++deepDozeCount
            }

            // Should we turn on battery saver mode?:
            if(checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED && !inBatterySaverMode() && allowBatterySaverControl )
            {
                // Yes:, try to enable the battery saver:
                if( !Settings.Global.putInt(this.contentResolver, "low_power", 1) )
                {
                    // Could not disable the battery saver!:
                    batterySaverError = "ERROR: Could not disable battery saver!"
                }
            }

            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED && disableWifiDuringDeepSleep && allowWifiControl ) {

                // Now, turn OFF wifi:
                var turnedOFFWifi = wifi.setWifiEnabled(false)// true or false to activate/deactivate wifi

                if (!turnedOFFWifi) {

                        Log.d(
                            "DEBUG/DOZE/WIFI_COULD_NOT_BE_OFF", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze + " wifi state =" + wifi.wifiState
                        )

                } else {

                    // Save the current Data:
                    lastWifiTurnOffTime = SimpleDateFormat("MMM d yyyy, hh:mm:ss a").format(Calendar.getInstance().time)

                    Log.d(
                        "DEBUG/DOZE/WIFI_OFF", "intent action=" + intent.action
                                + " idleMode=" + inDeepDoze
                    )
                }
            }

            // Can we handle turning on/off bluetooth?:
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED && disableBluetoothDuringDeepSleep && allowBlueToothControl) {

                // Are there no devices connected to bluetooth?:
                if ( deviceHasBluetooth() && !isBluetoothHeadsetConnected()) {

                    // No, we can turn it OFF:
                    val turnedOFFBluetooth = enableBluetooth(false)

                    // Could we not turn off bluetooth?:
                    if (!turnedOFFBluetooth) {
                        // No, log the error:
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_COULD_NOT_BE_OFF", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze
                        )
                    } else {
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_OFF", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze
                        )
                    }
                }
            }

        } else { // Deep doze maintenance, allow wifi/bluetooth and apps to post notifications:

            // Should we turn off battery saver mode?:
            if(checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED && inBatterySaverMode() && allowBatterySaverControl && !quietHours )
            {
                // Yes:, try to turn off the battery saver:
                if( !Settings.Global.putInt(this.contentResolver, "low_power", 0) )
                {
                    // Could not disable the battery saver!:
                    batterySaverError = "ERROR: Could not enable battery saver!"
                }
            }

            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED && disableWifiDuringDeepSleep && allowWifiControl && !quietHours) {
                // No, turn ON wifi:

                if( turnWifiBackOn ) {
                    var turnedONWifi = wifi.setWifiEnabled(true)

                    if (!turnedONWifi) {
                        Log.d(
                            "DEBUG/DOZE/WIFI_COULD_NOT_BE_ON", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze
                        )
                    } else {

                        Log.d(
                            "DEBUG/DOZE/WIFI_ON", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze
                        )
                    }
                }
            }

            // Can we handle turning on/off bluetooth?:
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED && disableBluetoothDuringDeepSleep && allowBlueToothControl && !quietHours) {

                // Are there no devices connected to bluetooth?:
                if (deviceHasBluetooth() && turnBluetoothBackOn ) {

                    // No, we can turn it ON:
                    val turnedONBluetooth = enableBluetooth(true)

                    // Could we not turn off bluetooth?:
                    if (!turnedONBluetooth) {
                        // No, log the error:
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_COULD_NOT_BE_ON", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze
                        )
                    } else {
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_ON", "intent action=" + intent.action
                                    + " idleMode=" + inDeepDoze
                        )
                    }

                }
            }
        }

        return quietHours
    }

    fun deviceHasBluetooth(): Boolean {
        val mBluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        return (mBluetoothAdapter != null)
    }

    fun isBluetoothOn(): Boolean {
        val bluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        var isON = false

        if(bluetoothAdapter != null)
        {
            isON = ( bluetoothAdapter.state == BluetoothAdapter.STATE_ON ||
                     bluetoothAdapter.state == BluetoothAdapter.STATE_TURNING_ON ||
                     bluetoothAdapter.isEnabled )
        }

        return isON
    }

    fun enableBluetooth(enable: Boolean): Boolean {
        val bluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        var isEnabled = false

        if ( bluetoothAdapter != null ) {
            //isEnabled = isBluetoothOn()

            if (enable) {
                return bluetoothAdapter.enable()
            } else {
                return bluetoothAdapter.disable()
            }
        }

        // No need to change bluetooth state
        return isEnabled
    }

    fun turnGPSOn() {
        val intent = Intent("android.location.GPS_ENABLED_CHANGE")
        intent.putExtra("enabled", true)
        sendBroadcast(intent)

    }

    // automatic turn off the gps
    fun turnGPSOff() {
        val intent = Intent("android.location.GPS_ENABLED_CHANGE")
        intent.putExtra("enabled", false)
        sendBroadcast(intent)
    }

    // Returns true if bluetooth is connected to something:
   fun isBluetoothHeadsetConnected() : Boolean {
        val mBluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        var headsetConnected = false

        if ( mBluetoothAdapter != null )
        {
            headsetConnected = ( mBluetoothAdapter.isEnabled &&
                                 mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED )
        }

       return headsetConnected
    }

//    fun isDevicePluggedIn() : Boolean {
//        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
//            registerReceiver(null, ifilter)
//        }
//
//        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
//        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
//                || status == BatteryManager.BATTERY_STATUS_FULL
//
//        // How are we charging?
//        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
////        val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
////        val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
//        // Will be true if the device is plugged into one of these power sources:
////                val pluggedIn: Boolean = ( chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
////                                           chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
////                                           chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
//
//        val pluggedIn = ( chargePlug > 0 || isCharging )
//
//        return pluggedIn
//    }

    // Will return true, if the device is in airplane mode:
    fun inAirplaneMode() : Boolean {
        // By default we will assume it's on, just in case we can't read the setting for some reaosn.
        // - If it's on, android will NOT allow us to control wifi, but bluetooth is OK.
        var airplaneModeOn = true

        try {
            airplaneModeOn = ( Settings.Global.getInt(this.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0 )
        }
        catch(ex: Exception)
        {
            Log.d( "inAirplaneMode()", ex.message)
        }

        return airplaneModeOn
    }

    // Will return true, if the device is in a battery saving mode:
    fun inBatterySaverMode() : Boolean {
        // By default we will assume it's on, just in case we can't read the setting for some reason:
        var batterySaverModeOn = true

        try {
            batterySaverModeOn = ( Settings.Global.getInt(this.contentResolver,"low_power", 0) != 0 )
        }
        catch(ex: Exception)
        {
            Log.d( "inAirplaneMode()", ex.message)
        }

        return batterySaverModeOn
    }

    @Nullable
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun updateNotificationContents(title: String, contentText : String) : Notification? {

        if(notification == null)
        {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(this,
                0 /* request code */,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT)

            notificationBuilder = NotificationCompat.Builder(this, title)
                //.setSound(RingtoneManager.getDefaultUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentTitle(title)
//            .setStyle(NotificationCompat.BigTextStyle()
//                .bigText(title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
                //.setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)

            notification = notificationBuilder?.build()
        }
        else // It's created already, so just update it:
        {
            // Only update the title & content text:
            notificationBuilder?.setContentTitle(title)
            notificationBuilder?.setContentText(contentText)

            notification = notificationBuilder?.build()

            val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Show the updated notification to the user:
            mNotificationManager.notify(BetterBatteryLifeService.PERSISTANT_NOTIFICATION_ID,
                                        notification)
        }

        return notification
    }

    private fun customNotification(title: String, notificationID: Int) {
        serviceStartDateTimeMessage = "Running since: "
        serviceStartDateTimeMessage += SimpleDateFormat("EEEE, MMM d yyyy, hh:mm:ss a").format(Calendar.getInstance().time)

        notification = updateNotificationContents(title, serviceStartDateTimeMessage)

        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val descriptionText = getString(R.string.abc_action_bar_home_description)

            val channel = NotificationChannel(title,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_LOW) // IMPORTANCE_LOW = no sound.

            mNotificationManager.createNotificationChannel(channel)

            startForeground(notificationID, notification)

        }
        else // It's Android N:
        {
            startForeground(notificationID, notification)
        }
    }

    private fun RegisterApplicationReceiver() {
        val filter = IntentFilter()
        filter.addAction(BetterBatteryLifeService.ACTION_APPLY_SETTINGS)
        filter.addAction(BetterBatteryLifeService.ACTION_GET_SETTINGS)

        applicationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                var result = false

                when( intent.action ) {
                    BetterBatteryLifeService.ACTION_APPLY_SETTINGS -> {
                        // The user is changing these settings via the MainActivity:
                        disableWifiDuringDeepSleep =
                            intent.getBooleanExtra(
                                BetterBatteryLifeService.SETTING_DISABLE_WIFI_DURING_DEEP_SLEEP,
                                true
                            )
                        disableBluetoothDuringDeepSleep = intent.getBooleanExtra(
                            BetterBatteryLifeService.SETTING_DISABLE_BLUETOOTH_DURING_DEEP_SLEEP,
                            true
                        )

                        if (CheckDOZEPermissions()) {
                            result = SetupOptimizedDozeParams(false)
                        }

                        val resultIntent = Intent(BetterBatteryLifeService.ACTION_APPLY_SETTINGS_COMPLETED)
                        resultIntent.putExtra("SUCCESS", result)

                        onReportBackToApplication (resultIntent)
                    }
                    else -> { // ACTION_GET_SETTINGS:
                        onActionGetSettings()
                    }

                }
            }
        }

        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager?.registerReceiver(applicationReceiver as BroadcastReceiver, filter)
    }

    private fun onActionGetSettings() {
        val resultIntent = Intent(BetterBatteryLifeService.ACTION_GET_SETTINGS_COMPLETED)

        val extraInfo = ArrayList<String>()
        extraInfo.add(serviceStartDateTimeMessage) // The service start time ( in the notification ).

        // Has the user unplugged the device, since we started running?:
        if(!lastUnplugTime.isNullOrEmpty()) {
            // See how long we have been unplugged in seconds:
            val unplugTimeDurationSections : Long = TimeUnit.NANOSECONDS.toSeconds(SystemClock.elapsedRealtimeNanos()) - unplugTimeSeconds

            // Format this into: days, hours, minutes & seconds:
            val lastUnplugedFormattedDuration = ellapsedTime( unplugTimeDurationSections )

            extraInfo.add(
                String.format(
                    "Last unplugged battery level: %.0f%%. ",
                    unplugBatteryPercentage
                )
            )

            // The last unplugged date:
            extraInfo.add("Unplugged on: " + lastUnplugTime)
            extraInfo.add("Last unplugged: " + lastUnplugedFormattedDuration + " ago.")
        }

        // TODO: This is not really needed:
        extraInfo.add("Screen OFF battery percentage " + batteryPercentageScreenOff.toString() + "%")

        extraInfo.add(
            String.format(
                "Lost: %.2f%% during last screen off.",
                screenOffBatteryDrainPerHour
            )
        )

        // Only record this info, if the screen off time is at least 1 second:
        if( TimeUnit.NANOSECONDS.toSeconds(screenOffTimeNanoSeconds ) > 0L) {
            // See how long that the screen has been off in seconds:
            val screenOffTimeDurationSeconds: Long =
                TimeUnit.NANOSECONDS.toSeconds(SystemClock.elapsedRealtimeNanos() - screenOffTimeNanoSeconds)

            // Format this into: days, hours, minutes & seconds:
            val screenOffTimeFormattedDuration = ellapsedTime(screenOffTimeDurationSeconds)

            extraInfo.add("Last screen OFF: " + screenOffTimeFormattedDuration + " ago.")
        }

        if (!lastDozeStartTime.isNullOrEmpty()) {
            // Show the last date of the last deep doze cycle:
            extraInfo.add("Last DOZE time: " + lastDozeStartTime)
        }

        if(!lastWifiTurnOffTime.isNullOrEmpty()) {
            // Show the last date of the last time wifi was turned off:
            extraInfo.add("Last WIFI OFF time: " + lastWifiTurnOffTime)
        }

        // If any sensor disable/enable errors are reported, show them:
        if(!sensorControlError.isNullOrEmpty()) {
            // Show the error:
            extraInfo.add(sensorControlError)
        }

        // If any battery saver errors are reported, show them:
        if(!batterySaverError.isNullOrEmpty()) {
            // Show the error:
            extraInfo.add(batterySaverError)
        }

        // Populate our settings to send back to the MainActivity:
        resultIntent.putExtra(BetterBatteryLifeService.SETTING_DISABLE_WIFI_DURING_DEEP_SLEEP,
            disableWifiDuringDeepSleep)

        resultIntent.putExtra(BetterBatteryLifeService.SETTING_DISABLE_BLUETOOTH_DURING_DEEP_SLEEP,
            disableBluetoothDuringDeepSleep)

        resultIntent.putStringArrayListExtra(BetterBatteryLifeService.SETTING_GET_EXTRA_INFO, extraInfo)

        // Populate the result code:
        resultIntent.putExtra("SUCCESS", true)

        onReportBackToApplication (resultIntent)
    }

    // Gets called when the user plugs in / unplugs power ( works with wireless & usb power sources also ).
    private fun RegisterPowerConnectionReceiver() {
        val filter = IntentFilter()

        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)

        powerConnectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
//                val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
//                val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
//                        || status == BatteryManager.BATTERY_STATUS_FULL
//
//                val chargePlug: Int = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
//
//                // Will be true if the device is plugged into one of these power sources:
//                val pluggedIn: Boolean = ( chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
//                                           chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
//                                           chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS
//                                         )

                when(intent.action) {
                    Intent.ACTION_POWER_DISCONNECTED -> { // User unplugged the power
                        // Reset the number of data points collected & the average for the screen off battery drain data:
                        screenOffBatteryPercentageNumPoints = 1f // Start on the 1st data point.
                        screenOffBatteryDrainPerHourSum = 0f
                        screenOffBatteryDrainPerHour = 0f

                        deepDozeCount = 1UL

                        // Save the current Date
                        lastUnplugTime = SimpleDateFormat("MMM d yyyy, hh:mm:ss a").format(Calendar.getInstance().time)

                        // Save the current time in seconds:
                        unplugTimeSeconds = TimeUnit.NANOSECONDS.toSeconds(SystemClock.elapsedRealtimeNanos())

                        // Save the current battery percentage:
                        unplugBatteryPercentage = getBatteryPercentage()

                        Log.d("RegisterPowerConnectionReceiver()", "User unplugged power")

                        // Update the persistent notification to show the original, default notification:
                        notification = updateNotificationContents(
                            "Service Info",
                            "Unplugged on: " + lastUnplugTime
                        )

                        // Make sure the GUI also get's updated:
                        onActionGetSettings()

                        // NOTE: The notification does not need to update until the screen get's turned back on,
                        //          as we are measuring the screen OFF battery drain only.

                        // Finally make sure that the doze settings are set to ours, just in case the google play store reverted them:
                        // Force enter deep sleep:
                        try {
                            if( CheckDOZEPermissions() ) {
                                SetupOptimizedDozeParams(false)
                            }

                        } catch (e: Exception) {

                            // show a toast notification if it failed:
                            var toast = Toast.makeText(
                                context, e.message,
                                Toast.LENGTH_LONG
                            )

                            toast.setGravity(
                                Gravity.CENTER,
                                0,
                                0
                            )

                            toast.show()
                        }
                    }

                    else -> { // User plugged in the power
                        // Reset the number of data points collected & the average for the screen off battery drain data:
                        screenOffBatteryPercentageNumPoints = 1f // Start on the 1st data point.
                        screenOffBatteryDrainPerHourSum = 0f
                        screenOffBatteryDrainPerHour = 0f

                        lastUnplugTime = ""
                        unplugTimeSeconds = 0

                        // Don't care about screen OFF battery drain, if it's plugged in
                        screenOffTimeNanoSeconds = 0
                        screenOffBatteryPercentageNumPoints = 1f
                        deepDozeCount = 1UL

                        // Update the persistent notification to show the original, default notification:
                        notification = updateNotificationContents(
                            "Service Info",
                            serviceStartDateTimeMessage // // Show the "Running Since...." message:
                        )

                        Log.d("RegisterPowerConnectionReceiver()", "User plugged in power")

                        // Make sure the GUI also get's updated:
                        onActionGetSettings()
                    }
                }
            }
        }

        registerReceiver(powerConnectionReceiver as BroadcastReceiver, filter)
    }

    private fun ellapsedTime(secondsElapsed: Long) : String{
        val day = TimeUnit.SECONDS.toDays(secondsElapsed).toInt()
        val hours =  TimeUnit.SECONDS.toHours(secondsElapsed) - day * 24
        val minute = TimeUnit.SECONDS.toMinutes(secondsElapsed) - TimeUnit.SECONDS.toHours(secondsElapsed) * 60
        val second = TimeUnit.SECONDS.toSeconds(secondsElapsed) - TimeUnit.SECONDS.toMinutes(secondsElapsed) * 60

        return String.format("%d Days, %d hours, %d minutes, %d seconds", day, hours, minute, second)
    }

    private fun onReportBackToApplication(intent: Intent) {
        try {
            localBroadcastManager?.sendBroadcast(intent)
        }
        catch (e:Exception)
        {
            Log.d("onReportBackToApplication()", e.message)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()

        if(dozeReceiver != null )
        {
            // Stop listening to DOZE events, we are shutting down:
            this.unregisterReceiver(dozeReceiver)
        }

       if(screenReceiver != null )
       {
           this.unregisterReceiver(screenReceiver)
       }

        if( bluetoothReceiver != null )
        {
            this.unregisterReceiver(bluetoothReceiver)
        }

        if( wifiReceiver != null )
        {
            this.unregisterReceiver(wifiReceiver)
        }

        if( applicationReceiver != null )
        {
            localBroadcastManager?.unregisterReceiver(applicationReceiver as BroadcastReceiver)
        }

        var toast = Toast.makeText(
            applicationContext, "BetterBatteryLifeService stopped.",
            Toast.LENGTH_LONG
        )

        toast.setGravity(
            Gravity.CENTER,
            0,
            0
        )

        toast.show()

        serviceRunning = false
    }
}

class Shell {

    fun Shell() {

    }

    fun RunCommand(command: String):String {

        var output:StringBuffer = StringBuffer()

        var p: Process

        try {
            p = Runtime.getRuntime().exec(command)
            p.waitFor()
            val reader = BufferedReader(InputStreamReader(p.inputStream))

            var line: String = ""

            while ( { line = reader.readLine(); line }() != null ) {
                output.append(line + "n")
            }

        } catch (e: java.lang.Exception) {
            //e.printStackTrace()
        }

        val response = output.toString()
        return response
    }
}