package com.MichaelAnzalone.betterbatterylife

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.view.Gravity
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.content.BroadcastReceiver
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.Nullable
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

class BetterBatteryLifeService : Service() {

    /** indicates whether onRebind should be used  */
    var mAllowRebind: Boolean = false

    var dozeReceiver: BroadcastReceiver? = null
    var screenReceiver: BroadcastReceiver? = null
    var bluetoothReceiver: BroadcastReceiver? = null
    var wifiReceiver: BroadcastReceiver? = null

    @Volatile var turnBluetoothBackOn = false
    @Volatile var turnWifiBackOn = false

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

        // Enable the persistant notification required, to keep this foreground
        //  service running forever:
        customNotification("Service Info", 101)

        // Listen for DOZE state changes:
        RegisterDozeState()

        // Listen for screen ON events:
        RegisterScreenState()

        // Listen for bluetooth events:
        // Not needed for now.
        //RegisterBluetoothState()

        // Listen for wifi events:
        // Not needed for now.
        //RegisterWifiState()

        if (!CheckDOZEPermissions()) {
            return
        }

        SetupOptimizedDozeParams(true)
    }

    fun SetupOptimizedDozeParams(showToastNotifications: Boolean) {
        val optimizedConfig = "inactive_to=10000," + // After 10 seconds, enter DEEP DOZE.
                "sensing_to=0," +                    // Ignore sensors when entering DEEP/Light DOZE.
                "light_after_inactive_to=1200000," + // After 20 minutes, enter light DOZE.
                "locating_to=0," +                   // Ignore location when entering DEEP/Light DOZE.
                "location_accuracy=20.0," +          // Not used if: "locating_to=0" is set.
                "motion_inactive_to=0," +
                "idle_after_inactive_to=0," +
                "idle_pending_to=30000," +
                "max_idle_pending_to=120000," +
                "idle_pending_factor=2.0," +
                "idle_to=1000000," +
                "max_idle_to=86400000," +
                "idle_factor=2.0," +
                "min_time_to_alarm=600000," + // 10 minutes.
                "max_temp_app_whitelist_duration=10000," +
                "mms_temp_app_whitelist_duration=10000," +
                "sms_temp_app_whitelist_duration=10000," +
                "light_idle_to=120000," +
                "light_idle_maintenance_min_budget=60000," +
                "light_idle_maintenance_max_budget=120000," +
                "wait_for_unlock=true" // May delay notifications on On Screen Displays...

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

            return@SetupOptimizedDozeParams
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
    }

    fun onScreenOff(intent: Intent) {

        // Save the states of WIFI and bluetooth, just in case the user changed them:
        turnWifiBackOn = isWifiOn()
        turnBluetoothBackOn = isBluetoothOn()
    }

    fun onScreenON(intent: Intent) {
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            // No, turn ON wifi:

            if( turnWifiBackOn ) {
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
    }

    fun RegisterScreenState() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val strAction = intent.action

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
                onDozeWifiEvent(intent)

                // If we have access to the doze settings, see if they have been changed by other service
                //  like the play services, ( which is known to revert them back every once and a while grrrrr. ) and
                //  change them if necessary.
                if (CheckDOZEPermissions()) {
                    SetupOptimizedDozeParams(false)
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

    fun onDozeWifiEvent(intent: Intent) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (pm.isDeviceIdleMode) // Entered DOZE?:
        {
            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {

                // Now, turn OFF wifi:
                val turnedOFFWifi = wifi.setWifiEnabled(false)// true or false to activate/deactivate wifi

                if (!turnedOFFWifi) {
                    Log.d(
                        "DEBUG/DOZE/WIFI_COULD_NOT_BE_OFF", "intent action=" + intent.action
                                + " idleMode=" + pm.isDeviceIdleMode
                    )
                } else {

                    Log.d(
                        "DEBUG/DOZE/WIFI_OFF", "intent action=" + intent.action
                                + " idleMode=" + pm.isDeviceIdleMode
                    )
                }
            }

            // Can we handle turning on/off bluetooth?:
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {

                // Are there no devices connected to bluetooth?:
                if ( deviceHasBluetooth() && !isBluetoothHeadsetConnected()) {

                    // No, we can turn it OFF:
                    val turnedOFFBluetooth = enableBluetooth(false)

                    // Could we not turn off bluetooth?:
                    if (!turnedOFFBluetooth) {
                        // No, log the error:
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_COULD_NOT_BE_OFF", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    } else {
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_OFF", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    }
                }
            }

        } else {
            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                // No, turn ON wifi:

                if( turnWifiBackOn ) {
                    var turnedONWifi = wifi.setWifiEnabled(true)

                    if (!turnedONWifi) {
                        Log.d(
                            "DEBUG/DOZE/WIFI_COULD_NOT_BE_ON", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    } else {

                        Log.d(
                            "DEBUG/DOZE/WIFI_ON", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
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
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    } else {
                        Log.d(
                            "DEBUG/DOZE/BLUETOOTH_ON", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    }

                }
            }
        }
    }

    fun deviceHasBluetooth(): Boolean {
        val mBluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        return (mBluetoothAdapter != null)
    }

    fun isBluetoothOn(): Boolean {
        val bluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        return  (bluetoothAdapter != null && ( bluetoothAdapter.state == BluetoothAdapter.STATE_ON ||
                                               bluetoothAdapter.state == BluetoothAdapter.STATE_TURNING_ON ||
                                               bluetoothAdapter.isEnabled ) )
    }

    fun enableBluetooth(enable: Boolean): Boolean {
        val bluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        var isEnabled = false

        if ( bluetoothAdapter != null ) {
            isEnabled = bluetoothAdapter.isEnabled
            if (enable && !isEnabled) {
                return bluetoothAdapter.enable()
            } else if (!enable && isEnabled) {
                return bluetoothAdapter.disable()
            }
        }

        // No need to change bluetooth state
        return true
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



    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
    }

    @Nullable
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun customNotification(title: String,notificationID: Int) {

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(this,
            0 /* request code */,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, title)
            //.setSound(RingtoneManager.getDefaultUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentTitle(title)
//            .setStyle(NotificationCompat.BigTextStyle()
//                .bigText(title))
            .setContentText("Running since: " + Calendar.getInstance().time.toString())
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
            //.setLargeIcon(BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher))
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)

        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val descriptionText = getString(R.string.abc_action_bar_home_description)

            val channel = NotificationChannel(title,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_LOW) // IMPORTANCE_LOW = no sound.


            mNotificationManager.createNotificationChannel(channel)


            startForeground(notificationID,notification)

        }
        else // It's Android N:
        {
            startForeground(notificationID, notification)
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

        var toast = Toast.makeText(
            applicationContext, "BetterBatteryLifeService stopped.",
            Toast.LENGTH_LONG
        )

        toast.setGravity(
            Gravity.CENTER,
            0,
            0
        )
    }
}