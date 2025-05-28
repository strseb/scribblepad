// ITunnelClient.aidl
package org.mozilla.guardian.tunnel;

// --> IMPORTANT: When changing the AIDL thange the version in TunnelConstants <<--!!
interface ITunnelClient {
    /**
    * @param {boolean} isConnected - Returns true if the service already is alive and Connected i.e
    * when the Service was Started due to Always on or a Widget.
    **/
    void init(boolean isConnected);
    // The Service has just connected to the VPN with pubkey
    void connected(String pubkey);
    // The Service has disconnected from the VPN
    void disconnected();
    // UUUH TODO: Define that...
    String statisticUpdate();
    void activationError();
    // The VPN Permission is not present.
    // The Client needs to fire the provided intent to trigger a VPN - Permission Prompt
    void permissionRequired(in String intentBase64);
    // The Client should fire a notification permission prompt to make sure the service
    // can run as bound foreground serivce.
    void requestNotificationPermission();
}