// TunnelPeer.aidl
package org.mozilla.guardian.tunnel;

parcelable TunnelPeer {
    String name;
    String ipv4AddrIn;
    String ipv4Gateway;
    String ipv6AddrIn;
    String ipv6Gateway;
    String publicKey;
    int port;
}