/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.mozilla.guardian.tunnel

import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.system.OsConstants
import com.wireguard.android.util.SharedLibraryLoader
import com.wireguard.config.Config
import org.json.JSONObject
import java.util.*

class TunnelService : android.net.VpnService() {
    private val settings = EncryptedSettings(this)
    private val tag = "VPNService"
    private var mBinder: TunnelBinder = TunnelBinder(this, settings)
    private var mConnectionTime: Long = 0
    private var mAlreadyInitialised = false

    private var currentConfig : TunnelConfig? = null
        get() {
            if(field == null){
                return settings.lastConfiguration
            }
            return field
        }
        set(value) {
            settings.lastConfiguration = value
            field = value;
        }



    // When provided a list of possible EndpointPeers
    // this index will choose the primary route.
    private var serverIndex = 0;

    private var currentTunnelHandle = -1
        set(value: Int) {
            field = value
            if (value > -1) {
                Log.i(tag, "Dispatch Daemon State -> connected")
                mBinder.registeredClients.forEach{
                    if(it.asBinder().isBinderAlive){
                        it.connected("")
                    }
                }
            }
            Log.i(tag, "Dispatch Daemon State -> disconnected")
            mBinder.registeredClients.forEach{
                if(it.asBinder().isBinderAlive){
                    it.disconnected()
                }
            }
        }

    private fun init() {
        if (mAlreadyInitialised) {
            Log.i(tag, "VPN Service already initialized, ignoring.")
            return
        }

        Log.init(this)
        // Check in with wg, if there is a tunnel.
        // This should be 99% -1, however if the service get's destroyed and the
        // wireguard tunnel lives on, we can recover from here :)
        currentTunnelHandle = WireGuardGo.wgGetLatestHandle()
        Log.i(tag, "Wireguard reported current tunnel: $currentTunnelHandle")
        mAlreadyInitialised = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!isUp) {
            Log.v(tag, "Client Disconnected, VPN is down - Service might shut down soon")
            return super.onUnbind(intent)
        }
        Log.v(tag, "Client Disconnected, VPN is up")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Note: This might not get called (depending on how it got invoked)
        // it for granted all exits will be here.
        Log.v(tag, "Service got Destroyed")
        super.onDestroy()
    }

    /**
     * EntryPoint for the Service, gets Called when anyone calls bindService.
     * Returns the [TunnelBinder] the client can send Requests to it.
     */
    override fun onBind(intent: Intent?): IBinder {
        Log.v(tag, "Got Bind request")
        init()
        return mBinder
    }

    /**
     * Might be the entryPoint if the Service gets Started via an Service Intent: Might be from
     * Always-On-Vpn from Settings or from Booting the device and having "connect on boot" enabled.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "Service Started by Intent")
        init()
        if (isUp) {
            // In case a user has "always-on" and "start-on-boot" enabled, we might
            // get this multiple times.
            return START_NOT_STICKY
        }
        // This start is from always-on
        // Check if we have a config, or if there is one in storage.
        if (currentConfig == null) {
            // We have nothing to connect to -> Exit
            Log.e(tag, "VPN service was triggered without defining a Server or having a tunnel")
            return super.onStartCommand(intent, flags, startId)
        }
        try {
            turnOn(currentConfig)
        } catch (error: Exception) {
            Log.e(tag, "Failed to start the VPN for always-on:")
            Log.e(tag, error.toString())
            Log.stack(tag, error.stackTrace)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Invoked when the application is revoked.
    // At this moment, the VPN interface is already deactivated by the system.
    override fun onRevoke() {
        Log.i(tag, "OS Revoked VPN permission")
        this.turnOff()
        super.onRevoke()
    }

    val connectionTime: Long
        get() {
            return mConnectionTime
        }

    /**
     * Checks if there is a config loaded or some available in the Storage to fetch. if this is
     * false calling {reconnect()} will abort.
     * @returns whether a config is found.
     */
    val canActivate: Boolean
        get() {
            return currentConfig != null;
        }
    val isUp: Boolean
        get() {
            return currentTunnelHandle >= 0
        }


    /*
     * Checks if the VPN Permission is given.
     * If the permission is given, returns true
     * Requests permission and returns false if not.
     */
    fun checkPermissions(): Intent? {
        // See https://developer.android.com/guide/topics/connectivity/vpn#connect_a_service
        // Call Prepare, if we get an Intent back, we don't have the VPN Permission
        // from the user. So we need to pass this to our main Activity and exit here.
        val intent = prepare(this)
        return intent
    }

    fun turnOn(config: TunnelConfig?) {
        if (config == null) {
            throw Error("no json config provided")
        }

        val wireguardConf =
            settings.wireguardPrivateKey?.let { config.toWireguardConfig(serverIndex, it) }
        if(wireguardConf == null){
            throw Error("Failed to generate wireguard conf")
        }
        val wgConfig: String = wireguardConf.toWgUserspaceString()
        if (wgConfig.isEmpty()) {
            throw Error("WG_Userspace config is empty, can't continue")
        }
        if (checkPermissions() != null) {
            throw Error("turn on was called without vpn-permission!")
        }

        val builder = Builder().apply {
            wireguardConf.applyTo(this)
            setSession("mvpn0")
        }
        builder.establish().use { tun ->
            if (tun == null) {
                throw Error("Android did not provide a TUN Handle")
            }
            // We should have everything to establish a new connection, turn down the old tunnel
            // now.
            if (currentTunnelHandle != -1) {
                WireGuardGo.wgTurnOff(currentTunnelHandle)
            }
            currentTunnelHandle = WireGuardGo.wgTurnOn("mvpn0", tun.detachFd(), wgConfig)
        }
        if (currentTunnelHandle < 0) {
            throw Error("Activation Error Wireguard-Error -> $currentTunnelHandle")
        } else {
            Log.i(tag, "Updated tunnel handle to: " + currentTunnelHandle)
        }
        protect(WireGuardGo.wgGetSocketV4(currentTunnelHandle))
        protect(WireGuardGo.wgGetSocketV6(currentTunnelHandle))

        currentConfig = config
        // Store the object in the settings
        settings.lastConfiguration = config

    }

    fun reconnect(nextServer: Boolean = false) {
        val config = currentConfig ?: return
        if(nextServer){
            serverIndex = ( (serverIndex+1) % config.servers.size)
        }
        Log.v(tag, "Try to reconnect tunnel with same conf")
        try{
            this.turnOn(config)
        } catch (e: Exception) {
            Log.e(tag, "VPN service - Reconnect failed")
        }
    }

    fun turnOff() {
        Log.v(tag, "Try to disable tunnel")
        WireGuardGo.wgTurnOff(currentTunnelHandle);
        currentTunnelHandle = -1
    }

    /** Gets config value for {key} from the Current running Wireguard tunnel */
    private fun getConfigValue(key: String): String? {
        if (!isUp) {
            return null
        }
        val data = WireGuardGo.wgGetConfig(currentTunnelHandle) ?: return null
        val lines = data.split("\n")
        for (line in lines) {
            val parts = line.split("=")
            val k = parts.first()
            val value = parts.last()
            if (key == k) {
                return value
            }
        }
        return null
    }
}
