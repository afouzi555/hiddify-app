package com.hiddify.hiddify.bg

import android.net.Network
import android.os.Build
import com.hiddify.hiddify.Application
import com.hiddify.core.libbox.InterfaceUpdateListener
import com.hiddify.hiddify.constant.Bugs


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.NetworkInterface

object DefaultNetworkMonitor {

    var defaultNetwork: Network? = null
    private var listener: InterfaceUpdateListener? = null

    suspend fun start() {
        DefaultNetworkListener.start(this) {
            defaultNetwork = it
            checkDefaultInterfaceUpdate(it)
        }
        defaultNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Application.connectivity.activeNetwork
        } else {
            DefaultNetworkListener.get()
        }
    }

    suspend fun stop() {
        DefaultNetworkListener.stop(this)
    }

    suspend fun require(): Network {
        val network = defaultNetwork
        if (network != null) {
            return network
        }
        return DefaultNetworkListener.get()
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        val listener = listener ?: return
        if (newNetwork != null) {
            val interfaceName =
                (Application.connectivity.getLinkProperties(newNetwork) ?: return).interfaceName
            // BUG FIX (2026-09-03): this loop is a retry-until-success wrapper around
            // NetworkInterface.getByName (which can transiently fail right after a network
            // change while the OS is still updating its interface table) -- it must call
            // the Go-side listener.updateDefaultInterface() EXACTLY ONCE per real change.
            // The original loop had no  after a successful call, so on any attempt
            // that succeeded immediately (the common case) it fired the SAME update 10
            // times in a tight loop with no delay between them. Each call is a JNI
            // round-trip into gomobile's Go<->Kotlin reference-counted object bridge;
            // repeated rapid-fire reentrant calls corrupt that shared reference table on
            // some devices, producing a native SIGABRT ("Unknown reference: N") --
            // reproduced consistently on a dual-SIM OnePlus Nord 5G (far more frequent
            // network-change events than lower-churn devices), never on other test
            // devices. Root cause confirmed via native crash backtrace pointing at
            // go_seq_from_refnum / PlatformInterface.GetInterfaces.
            for (times in 0 until 10) {
                var interfaceIndex: Int
                try {
                    interfaceIndex = NetworkInterface.getByName(interfaceName).index
                } catch (e: Exception) {
                    Thread.sleep(100)
                    continue
                }
                listener.updateDefaultInterface(interfaceName, interfaceIndex, false, false)
                break
            }
        } else {
            listener.updateDefaultInterface("", -1, false, false)
        }
    }
}