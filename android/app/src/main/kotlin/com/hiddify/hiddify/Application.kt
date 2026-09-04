package com.hiddify.hiddify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.hiddify.hiddify.bg.AppChangeReceiver
import go.Seq
import com.hiddify.hiddify.Application as BoxApplication
import android.system.Os
import android.util.Log

class Application : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    override fun onCreate() {
        super.onCreate()

        // D156 -- diagnostic only, investigating the Nord 5G "Unknown reference: 42" crash
        // (go/Seq abort, gomobile JNI reference bridge). GODEBUG must be set via the process's
        // own environment BEFORE the Go runtime initializes -- Seq's static initializer (fired
        // by the very next line, Seq.setContext()) is what calls System.loadLibrary("gojni"),
        // which is when Go's runtime actually starts. gctrace=1 makes Go print one line per GC
        // cycle (gc N @Ts ...) to stderr, which Libbox.redirectStderr() already routes to
        // stderr.log -- pull that file after a reproduction and compare its GC-cycle timestamps
        // directly against the [log-hcore-connect] step timestamps and the native crash time,
        // to confirm or rule out a GC-cycle correlation before attempting another fix.
        try {
            Os.setenv("GODEBUG", "gctrace=1", true)
        } catch (e: Exception) {
            Log.e("A/Application", "failed to set GODEBUG=gctrace=1", e)
        }

        Seq.setContext(this)

        registerReceiver(AppChangeReceiver(), IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        })
    }

    companion object {
        lateinit var application: BoxApplication
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val packageManager by lazy { application.packageManager }
        val powerManager by lazy { application.getSystemService<PowerManager>()!! }
        val notificationManager by lazy { application.getSystemService<NotificationManager>()!! }

        val wifiManager by lazy { application.getSystemService<WifiManager>()!! }

    }

}