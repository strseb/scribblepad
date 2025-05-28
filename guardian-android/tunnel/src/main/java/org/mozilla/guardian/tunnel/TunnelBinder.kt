package org.mozilla.guardian.tunnel

import android.content.Intent


class TunnelBinder(
    private val service: TunnelService,
    private val settings: EncryptedSettings
) : ITunnelService.Stub(
) {
    val registeredClients = mutableListOf<ITunnelClient>()

    override fun version(): Int {
        return TunnelConstants.VERSION;
    }

    override fun getPermissionIntent(): String {
        val intent = service.checkPermissions() ?: return "";
        return intent.toBase64();
    }

    override fun activate(conf: TunnelConfig?): Boolean {
        try {
            service.turnOn(conf)
        }catch (e:Exception){
            return false;
        }
        return true;
    }

    override fun activateLastConfiguration() {
        if(service.canActivate){
            service.reconnect()
        }
    }

    override fun switchTo(conf: TunnelConfig?): Boolean {
        TODO("Not yet implemented")
    }

    override fun deactivate() {
        service.turnOff()

    }

    override fun getStatus(): Int {
        if(service.isUp){
            return TunnelStatus.ON;
        }
        return TunnelStatus.OFF;
    }

    override fun publicKey(): String {
        val key = settings.wireguardPublicKey ?: return ""
        return key.toBase64()
    }

    override fun registerClient(listener: ITunnelClient?, version: Int) {
        if(listener == null){
            return;
        }
        if (version != TunnelConstants.VERSION) {
            Log.e("Tunnelbinder","Client version mismatch: $version vs ${TunnelConstants.VERSION}")
            return
        }
        registeredClients += listener
    }
}