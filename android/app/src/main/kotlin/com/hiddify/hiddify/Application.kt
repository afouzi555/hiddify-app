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

        // D156 -- SUPERSEDED BY D157, kept only because it's harmless (see below). Originally
        // added to investigate the Nord 5G "Unknown reference: 42" crash (go/Seq abort,
        // gomobile JNI reference bridge) by making Go's runtime print one GC-cycle trace line
        // per cycle to stderr, on the theory that Libbox.redirectStderr() (BoxService.kt)
        // would capture it in on-device stderr.log for comparison against the crash timing.
        //
        // That theory was WRONG, confirmed by testing this exact patch on-device: stderr.log
        // stayed empty even after ~28 minutes of runtime. Root cause (see hiddify-core's D157
        // commit message and OPTIMUS_VPN_SECURITY_PRIVACY_DOC.md section 9.4.3 for the full
        // writeup): Libbox.redirectStderr() doesn't redirect stderr at all -- it wires Go
        // 1.23+'s debug.SetCrashOutput(), which only captures a fatal Go runtime crash dump,
        // never GODEBUG=gctrace's periodic output (written straight to the raw OS-level fd 2,
        // bypassing SetCrashOutput entirely). The real GC-timing diagnostic that actually
        // worked lives in hiddify-core's v2/hcore/start.go (hclogGCStats(), D157) instead,
        // polling runtime.ReadMemStats() through the already-proven hclog()/app.log channel.
        //
        // GODEBUG must still be set via the process's own environment BEFORE the Go runtime
        // initializes -- Seq's static initializer (fired by the very next line,
        // Seq.setContext()) is what calls System.loadLibrary("gojni"), which is when Go's
        // runtime actually starts -- so this remains a technically-correct place to set any
        // GODEBUG flag; gctrace=1 specifically is just pointless now since nothing reads its
        // output. Left in place (rather than removed) because it's inert and harmless, and a
        // future agent adding a DIFFERENT GODEBUG flag that Go's runtime.SetCrashOutput or
        // logcat *can* surface would want this exact injection point.
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