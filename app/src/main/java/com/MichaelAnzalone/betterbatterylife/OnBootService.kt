package com.MichaelAnzalone.betterbatterylife

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.MichaelAnzalone.betterbatterylife.MainActivity
import android.os.IBinder
import android.app.Service
import androidx.core.content.ContextCompat.startForegroundService
import android.os.Build



// Starts the BetterBatteryLifeServiceActivity:
class OnBootService : BroadcastReceiver()
{
    override fun onReceive(context: Context, intent: Intent) {

        val i = Intent(context, BetterBatteryLifeService::class.java)

        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || intent.action == Intent.ACTION_BOOT_COMPLETED) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService( i )
            } else {
                context.startService( i )
            }
        }
    }
}
