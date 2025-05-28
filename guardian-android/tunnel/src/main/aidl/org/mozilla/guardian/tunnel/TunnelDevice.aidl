// TunnelDevice.aidl
package org.mozilla.guardian.tunnel;


// --> IMPORTANT: When changing the AIDL thange the version in TunnelConstants <<--!!
parcelable TunnelDevice {
    String publicKey;
    String name;
    String createdAt;
    String ipv4Address;
    String ipv6Address;
}