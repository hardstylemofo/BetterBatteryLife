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
import android.app.ActionBar
import android.app.Activity
import android.app.PendingIntent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.hardware.SensorManager
import android.os.AsyncTask
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.MichaelAnzalone.betterbatterylife.BetterBatteryLifeService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    var versionCode = BuildConfig.VERSION_CODE
    var version = BuildConfig.VERSION_NAME

    var localBroadcastManager : LocalBroadcastManager? = null
    var serverReceiver: BroadcastReceiver? = null

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

    fun CheckDOZEPermissions() : Boolean {

        // Must be safe
        var requiredPermission = "android.permission.WRITE_SECURE_SETTINGS"
        var checkVal = checkSelfPermission(requiredPermission)

        if(checkVal == PackageManager.PERMISSION_DENIED) {
            return false
        }

        return true
    }

    fun CheckSensorPermissions() : Boolean {

        // Must be safe
        var requiredPermission = "android.permission.DUMP"
        var checkVal = checkSelfPermission(requiredPermission)

        if(checkVal == PackageManager.PERMISSION_DENIED) {
            return false
        }

        return true
    }

    fun CheckRequiredPermissions() : Boolean {

        // Must be safe
        var requiredPermission = "android.permission.WRITE_SECURE_SETTINGS"
        var checkVal = checkSelfPermission(requiredPermission)

        if( checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_DENIED ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CHANGE_WIFI_STATE),
                USER_REQUESTED_WIFI_ACCESS)

        }
//
//        if (checkVal == PackageManager.PERMISSION_DENIED) {
//
//            var toast = Toast.makeText(
//                this, "WRITE_SECURE_SETTINGS has NOT been granted to the application!",
//                Toast.LENGTH_LONG
//            )
//
//            toast.setGravity(
//                Gravity.BOTTOM,
//                0,
//                btnUpdateSettings.height + 100
//            )
//
//            toast.show()
//
//            return false
//        }


        if (checkVal == PackageManager.PERMISSION_DENIED) {

            message.text = "WARNING: WRITE_SECURE_SETTINGS has NOT been granted to the application!"

            return false
        }


//        if( !CheckSensorPermissions() ) {
//
//            message.text = "WARNING: DUMP Permission has NOT been granted to the application!"
//
//            return false
//        }

        return true
    }

    fun StartServiceIfNotStarted() {
        // Make sure the service is running:
        val i = Intent(Intent.ACTION_BOOT_COMPLETED)
        val m = OnBootService()
        m.onReceive(this, i)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        // Listen for events from our the BetterBatteryLifeService:
        RegisterServerReceiver()

        // Make sure the service is started ( it already should be on boot ):
        StartServiceIfNotStarted()

        btnUpdateSettings.setOnClickListener{
            btnUpdateSettings.isEnabled = false
            chbDisableBluetoothDeepSleep.isEnabled = false
            chbDisableWifiDeepSleep.isEnabled = false

            var intent = Intent(BetterBatteryLifeService.ACTION_APPLY_SETTINGS)
            intent.putExtra(BetterBatteryLifeService.SETTING_DISABLE_WIFI_DURING_DEEP_SLEEP, chbDisableWifiDeepSleep.isChecked )
            intent.putExtra(BetterBatteryLifeService.SETTING_DISABLE_BLUETOOTH_DURING_DEEP_SLEEP, chbDisableBluetoothDeepSleep.isChecked )

            // Tell the service to update it's settings:d
            var result: Boolean? = localBroadcastManager?.sendBroadcast(intent)
        }

        // Ask the service to retrieve it's settings:
        var intent = Intent(BetterBatteryLifeService.ACTION_GET_SETTINGS)
        var result: Boolean? = localBroadcastManager?.sendBroadcast(intent)
    }

    public override fun onResume() {
        super.onResume()  // Always call the superclass method first

        btnUpdateSettings.isEnabled = false
        chbDisableBluetoothDeepSleep.isEnabled = false
        chbDisableWifiDeepSleep.isEnabled = false

        if (!CheckRequiredPermissions() || !CheckDOZEPermissions())
        {
            return
        }

        // TODO: Why do I have to do this??
        btnUpdateSettings.isEnabled = true
        chbDisableBluetoothDeepSleep.isEnabled = true
        chbDisableWifiDeepSleep.isEnabled = true

        // Ask the service to retrieve it's settings:
        var intent = Intent(BetterBatteryLifeService.ACTION_GET_SETTINGS)
        var result: Boolean? = localBroadcastManager?.sendBroadcast(intent)

        message.text = "Your device's standby battery life is being optimized!"
    }

    public override fun onDestroy() {
        super.onDestroy()

        if( serverReceiver != null )
        {
            localBroadcastManager?.unregisterReceiver(serverReceiver as BroadcastReceiver)
        }
    }

    private fun RegisterServerReceiver() {
        val filter = IntentFilter()
        filter.addAction(BetterBatteryLifeService.ACTION_APPLY_SETTINGS_COMPLETED)
        filter.addAction(BetterBatteryLifeService.ACTION_GET_SETTINGS_COMPLETED)

        serverReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                var result = intent.getBooleanExtra("SUCCESS", false)

                if(intent.action == BetterBatteryLifeService.ACTION_APPLY_SETTINGS_COMPLETED) {
                    var message = "BetterBatteryLifeService settings"
                    val successMessage = " applied!"
                    val errorMessage = " not applied!"

                    when (result) {
                        true -> message += successMessage
                        false -> message += errorMessage
                    }


                    var toast = Toast.makeText(
                        applicationContext, message,
                        Toast.LENGTH_LONG
                    )

                    toast.setGravity(
                        Gravity.CENTER,
                        0,
                        0
                    )

                    toast.show()
                }
                else  // BetterBatteryLifeService.ACTION_GET_SETTINGS_COMPLETED
                {
                    // We got the server settings, let's update the GUI:
                    // See how long we have been unplugged:
                    val extraInfo: ArrayList<String> = intent.getStringArrayListExtra(BetterBatteryLifeService.SETTING_GET_EXTRA_INFO)

                    // Clear out the label:
                    lblInfo.text = ""

                    // Put the extraInfo in the label:
                    for (currentString in extraInfo)
                        lblInfo.append(currentString + "\n")

                    // Make sure the switches match the server's settings:
                    chbDisableWifiDeepSleep.isChecked  =
                        intent.getBooleanExtra(
                            BetterBatteryLifeService.SETTING_DISABLE_WIFI_DURING_DEEP_SLEEP,
                            true
                        )

                    chbDisableBluetoothDeepSleep.isChecked  = intent.getBooleanExtra(
                        BetterBatteryLifeService.SETTING_DISABLE_BLUETOOTH_DURING_DEEP_SLEEP,
                        true
                    )
                }

                btnUpdateSettings.isEnabled = true
                chbDisableBluetoothDeepSleep.isEnabled = true
                chbDisableWifiDeepSleep.isEnabled = true
            }
        }

        localBroadcastManager?.registerReceiver(serverReceiver as BroadcastReceiver, filter)
    }
}
