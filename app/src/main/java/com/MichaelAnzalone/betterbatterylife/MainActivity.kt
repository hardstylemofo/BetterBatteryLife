package com.MichaelAnzalone.betterbatterylife

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Gravity
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import android.content.IntentFilter
import android.content.Context.POWER_SERVICE
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.Manifest
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_home -> {
                message.setText(R.string.title_home)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_dashboard -> {
                message.setText(R.string.title_dashboard)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_notifications -> {
                message.setText(R.string.title_notifications)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    fun RegisterDozeState() {
        val filter = IntentFilter()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                var wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager

                if(pm.isDeviceIdleMode) // Entered DOZE?:
                {
                    if( checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED ) {

                        // Yes, turn OFF wifi:
                        val turnedOFFWifi = wifi.setWifiEnabled(false)// true or false to activate/deactivate wifi

                        if( !turnedOFFWifi ) {
                            Log.d(
                                "DEBUG/DOZE/WIFI_COULD_NOT_BE_OFF", "intent action=" + intent.action
                                        + " idleMode=" + pm.isDeviceIdleMode
                            )
                        }

                        Log.d(
                            "DEBUG/DOZE/WIFI_OFF", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    }
                }
                else
                {
                    if( checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED ) {
                        // No, turn ON wifi:
                        val turnedONWifi = wifi.setWifiEnabled(true)

                        if( !turnedONWifi ) {
                            Log.d(
                                "DEBUG/DOZE/WIFI_COULD_NOT_BE_ON", "intent action=" + intent.action
                                        + " idleMode=" + pm.isDeviceIdleMode
                            )
                        }

                        Log.d(
                            "DEBUG/DOZE/WIFI_ON", "intent action=" + intent.action
                                    + " idleMode=" + pm.isDeviceIdleMode
                        )
                    }
                }
            }
        }, filter)
    }

    private val USER_REQUESTED_WIFI_ACCESS = 1

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            USER_REQUESTED_WIFI_ACCESS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    Log.d(
                        "DEBUG/USER_GRANTED_WIFI_PERMISSIONS", ""
                    )

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                    Log.d(
                        "DEBUG/USER_DENIED_WIFI_PERMISSIONS", ""
                    )
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Must be safe
        val requiredPermission = "android.permission.WRITE_SECURE_SETTINGS"
        val checkVal = checkSelfPermission(requiredPermission)

        if( checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_DENIED ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CHANGE_WIFI_STATE),
                                            USER_REQUESTED_WIFI_ACCESS)

        }

        RegisterDozeState()

        if (checkVal == PackageManager.PERMISSION_DENIED) {

            var toast = Toast.makeText(
                this, "WRITE_SECURE_SETTINGS has NOT been granted to the application!",
                Toast.LENGTH_LONG
            )

            toast.setGravity(
                Gravity.BOTTOM,
                0,
                btnUpdateSettings.height + 100
            )

            toast.show()


            return@onCreate
        }



        // Enable the button"
        btnUpdateSettings.isEnabled = true

        btnUpdateSettings.setOnClickListener{


            // Must be safe
            val requiredPermission = "android.permission.WRITE_SECURE_SETTINGS"
            val checkVal = checkCallingOrSelfPermission(requiredPermission)

            if (checkVal == PackageManager.PERMISSION_DENIED) {

                var toast = Toast.makeText(
                    this, "WRITE_SECURE_SETTINGS has NOT been granted to the application!",
                    Toast.LENGTH_LONG
                )

                toast.setGravity(
                    Gravity.BOTTOM,
                    0,
                    btnUpdateSettings.height + 100
                )

                toast.show()


                return@setOnClickListener
            }


            val mParser = KeyValueListParser(',')

                val optimizedConfig = "inactive_to=15000," +
                            "sensing_to=0,"      +
                            "locating_to=0,"     +
                            "location_accuracy=20.0," +
                            "motion_inactive_to=0," +
                            "idle_after_inactive_to=0," +
                            "idle_pending_to=60000," +
                            "max_idle_pending_to=120000," +
                            "idle_pending_factor=2.0," +
                            "idle_to=900000," +
                            "max_idle_to=86400000," +
                            "idle_factor=2.0," +
                            "min_time_to_alarm=600000," +
                            "max_temp_app_whitelist_duration=10000," +
                            "mms_temp_app_whitelist_duration=10000," +
                            "sms_temp_app_whitelist_duration=10000"

                val config = Settings.Global.getString(baseContext.contentResolver,
                    "device_idle_constants")

                mParser.setString(config)

                try {
                    Settings.Global.putString(baseContext.contentResolver,
                        "device_idle_constants",
                        optimizedConfig)
                }
                catch (e: Exception) {

                    var toast = Toast.makeText(
                        this, e.message,
                        Toast.LENGTH_LONG
                    )

                    toast.setGravity(
                        Gravity.BOTTOM,
                        0,
                        btnUpdateSettings.height + 100
                    )

                    toast.show()

                    return@setOnClickListener
                }

                val config2 = Settings.Global.getString(baseContext.contentResolver,
                    "device_idle_constants")


            if ( config2 == optimizedConfig ) {

                var toast = Toast.makeText(
                    this, "Settings updated!",
                    Toast.LENGTH_SHORT
                )

                toast.setGravity(
                        Gravity.BOTTOM,
                        0,
                        btnUpdateSettings.height + 100
                )

                toast.show()
            }
            else
            {
                var toast2 = Toast.makeText(
                    this, "The settings were not updated!",
                    Toast.LENGTH_LONG
                )

                toast2.setGravity(
                    Gravity.BOTTOM,
                    0,
                    btnUpdateSettings.height + 100
                )

                toast2.show()

            }

  //          val proker = new SystemPropPoker()

    //        proker.execute()

//                // Code here executes on main thread after user presses button
//                val alertDialog = AlertDialog.Builder(this@MainActivity).create()
//                alertDialog.setTitle("Alert")
//                alertDialog.setMessage("Alert message to be shown")
//                alertDialog.setButton(
//                    AlertDialog.BUTTON_NEUTRAL, "OK",
//                    DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() })
//                alertDialog.show()
        }

    }
}
