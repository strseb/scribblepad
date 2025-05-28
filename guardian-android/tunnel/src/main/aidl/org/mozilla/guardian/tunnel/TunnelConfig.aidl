package org.mozilla.guardian.tunnel;

import org.mozilla.guardian.tunnel.TunnelDevice;
import org.mozilla.guardian.tunnel.TunnelPeer;

// --> IMPORTANT: When changing the AIDL thange the version in TunnelConstants <<--!!
parcelable TunnelConfig {
    // The LAN Device Info from Guardian
    TunnelDevice device;
    // The allowed ip adress range the network adapter should allow to route
    // If empty *all addresses are tunneled*
    List<String> allowedIPAddressRanges;

    // The Servers to Consider to Connect to
    // The first one will be used by default all others provided are considered backup
    List<TunnelPeer> servers;

    // A list of the application packages that should not be able to use the VPN
    List<String> excludedApps;

    String serverHost;
    List<String> dnsServers;
    int serverPort;
    boolean useCustomDns;
}
