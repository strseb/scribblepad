package org.mozilla.guardian.tunnel


import android.os.Build
import android.system.OsConstants
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair


fun TunnelPeer.toWireguardPeer(config : TunnelConfig): com.wireguard.config.Peer {
    val peer = this;
    val builder = Peer.Builder().apply {
        setEndpoint(
            InetEndpoint.parse(peer.ipv4AddrIn+":"+peer.port)
        )
        setPublicKey(Key.fromBase64(peer.publicKey))
        if(config.allowedIPAddressRanges.isEmpty()){
            addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
        } else{
            config.allowedIPAddressRanges.forEach {
                val network = InetNetwork.parse(it)
                addAllowedIp(network)
            }
        }
    }
    return builder.build()
}

fun TunnelDevice.toWireguardDevice(pk: Key, config: TunnelConfig, endpoint: TunnelPeer ): com.wireguard.config.Interface{
    val device = this;
    val builder = Interface.Builder()
    return builder.apply {
        setKeyPair(KeyPair(pk))
        addAddress(InetNetwork.parse(device.ipv4Address))
        addAddress(InetNetwork.parse(device.ipv6Address))

        if(config.dnsServers.isNotEmpty()){
            config.dnsServers.forEach{
                addDnsServer(InetNetwork.parse(it).address)
            }
        } else {
            addDnsServer(InetNetwork.parse(endpoint.ipv4Gateway).address)
            addDnsServer(InetNetwork.parse(endpoint.ipv6Gateway).address)
        }
        config.excludedApps.forEach {
            excludeApplication(it)
        }
    }.build()
}

fun TunnelConfig.toWireguardConfig(selectedServer: Int, privateKey: com.wireguard.crypto.Key): com.wireguard.config.Config {
    if(selectedServer > this.servers.size){
        throw Error("Selected server out of index")
    }
    val config = this
    val server = this.servers.getOrElse(selectedServer) { this.servers[0] }
    return Config.Builder().apply {
        addPeer(server.toWireguardPeer(config))
        setInterface(config.device.toWireguardDevice(privateKey,config,server))
    }.build()
}

fun com.wireguard.config.Config.applyTo(builder: android.net.VpnService.Builder) {
    // Setup Split tunnel
    for (
    excludedApplication in
    this.`interface`.excludedApplications
    ) builder.addDisallowedApplication(
        excludedApplication,
    )

    // Device IP
    for (addr in this.`interface`.addresses) builder.addAddress(addr.address, addr.mask)
    // DNS
    for (addr in this.`interface`.dnsServers) addr.hostAddress?.let { builder.addDnsServer(it) }
    // Add All this the VPN may route tos
    for (peer in this.peers) {
        for (addr in peer.allowedIps) {
            builder.addRoute(addr.address, addr.mask)
        }
    }
    builder.allowFamily(OsConstants.AF_INET)
    builder.allowFamily(OsConstants.AF_INET6)
    builder.setMtu(this.`interface`.mtu.orElse(1280))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

    builder.setBlocking(true)
}

