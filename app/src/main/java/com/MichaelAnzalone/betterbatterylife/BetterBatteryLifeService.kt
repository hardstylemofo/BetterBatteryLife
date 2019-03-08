package com.MichaelAnzalone.betterbatterylife

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.view.Gravity
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.os.Build
import androidx.annotation.Nullable
import androidx.core.content.getSystemService
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.os.Parcel
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class BetterBatteryLifeService : Service() {

    var versionCode = BuildConfig.VERSION_CODE
    var versionName = BuildConfig.VERSION_NAME

    /** indicates whether onRebind should be used  */
    var mAllowRebind: Boolean = false

    var dozeReceiver: BroadcastReceiver? = null

    var screenReceiver: BroadcastReceiver? = null

    fun CheckDOZEPermissions() : Boolean {

        // Must be safe
        var requiredPermission = "android.permission.WRITE_SECURE_SETTINGS"
        var checkVal = checkSelfPermission(requiredPermission)

        if(checkVal == PackageManager.PERMISSION_DENIED) {
            return false
        }

        return true
    }

    /** Called when the service is being created.  */
    override fun onCreate() {
        super.onCreate()

        customNotification("Service Info",101)

        // Listen for DOZE state changes:
        RegisterDozeState()

        // Listen for screen ON events:
        RegisterScreenState()

        if(!CheckDOZEPermissions()){
            return
        }

        SetupOptimizedDozeParams()
    }

    fun SetupOptimizedDozeParams() {
        val optimizedConfig = "inactive_to=10000," +
                    "sensing_to=0," +
                    "locating_to=0," +
                    "location_accuracy=20.0," +
                    "motion_inactive_to=0," +
                    "idle_after_inactive_to=0," +
                    "idle_pending_to=30000," +
                    "max_idle_pending_to=120000," +
                    "idle_pending_factor=2.0," +
                    "idle_to=1000000," +
                    "max_idle_to=86400000," +
                    "idle_factor=2.0," +
                    "min_time_to_alarm=600000," +
                    "max_temp_app_whitelist_duration=10000," +
                    "mms_temp_app_whitelist_duration=10000," +
                    "sms_temp_app_whitelist_duration=10000," +
                    "light_idle_to=120000," +
                    "light_idle_maintenance_min_budget=60000," +
                    "light_idle_maintenance_max_budget=120000," +
                    "wait_for_unlock=true" // May delay notifications on On Screen Displays...

        val config = Settings.Global.getString(baseContext.contentResolver,
            "device_idle_constants")

        try {
            Settings.Global.putString(baseContext.contentResolver,
                "device_idle_constants",
                optimizedConfig)
        }
        catch (e: Exception) {

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

            return@SetupOptimizedDozeParams
        }

        val config2 = Settings.Global.getString(baseContext.contentResolver,
            "device_idle_constants")


        if ( config2 == optimizedConfig ) {

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
        else
        {
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

    fun RegisterScreenState() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)

        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val strAction = intent.action

                if (strAction == Intent.ACTION_SCREEN_ON) {

                    if( checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED ) {
                        // No, turn ON wifi:
                        val turnedONWifi = wifi.setWifiEnabled(true)

                        if( !turnedONWifi ) {
                            Log.d(
                                "DEBUG/SCREEN/WIFI_COULD_NOT_BE_ON", "intent action=" + intent.action

                            )
                        }
                        else {

                            Log.d(
                                "DEBUG/SCREEN/WIFI_ON", "intent action=" + intent.action

                            )
                        }
                    }

                }
            }
        }

        registerReceiver( screenReceiver, filter )
    }

    fun RegisterDozeState() {
        val filter = IntentFilter()
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)

        dozeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                onDozeWifiEvent(intent)

            }//, filter)
        }

        registerReceiver( dozeReceiver, filter )
    }

    fun onDozeWifiEvent(intent: Intent) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (pm.isDeviceIdleMode) // Entered DOZE?:
        {
            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {

                // Yes, turn OFF wifi:
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
        } else {
            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                // No, turn ON wifi:
                val turnedONWifi = wifi.setWifiEnabled(true)

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

        //val nManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        //nManager.cancel(R.string.remote_service_started)

        if(dozeReceiver != null )
        {
            // Stop listening to DOZE events, we are shutting down:
            this.unregisterReceiver(dozeReceiver)
        }

       if(screenReceiver != null )
       {
           this.unregisterReceiver(screenReceiver)
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