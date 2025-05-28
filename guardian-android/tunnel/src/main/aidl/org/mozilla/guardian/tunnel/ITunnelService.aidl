// ITunnelService.aidl
package org.mozilla.guardian.tunnel;
import org.mozilla.guardian.tunnel.ITunnelClient;
import org.mozilla.guardian.tunnel.TunnelConfig;
import org.mozilla.guardian.tunnel.TunnelStatus;


// --> IMPORTANT: When changing the AIDL thange the version in TunnelConstants <<--!!
interface ITunnelService {
    int version();
    String getPermissionIntent();
    boolean activate(in TunnelConfig conf);
    void activateLastConfiguration();
    boolean switchTo(in TunnelConfig conf);
    void deactivate();
    TunnelStatus getStatus();
    String publicKey();

    /**
    * Registers ITunnelClient to recieve Callbacks
    * the Version should be used from TunnelConstants
    **/
    void registerClient(ITunnelClient listener, int version);
}