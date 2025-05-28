package org.mozilla.guardian.tunnel;


// --> IMPORTANT: When changing the AIDL thange the version in TunnelConstants <<--!!
@Backing(type="int")
enum TunnelStatus {
    ON = 0,
    OFF = 1,
    SWITCHING = 2
}
