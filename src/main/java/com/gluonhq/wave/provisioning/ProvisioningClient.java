package com.gluonhq.wave.provisioning;

import signalservice.DeviceMessages;

/**
 *
 * @author johan
 */
public interface ProvisioningClient {
    
    /**
     * The ProvisioningManager has a url for us to display
     * @param url 
     */
    public void gotProvisioningUrl(String url);
    
    /**
     * A provisioning message is available for this client.
     * It is recommended for the client to now invoke ProvisioningManager.createAccount()
     * @param number 
     */
    public void gotProvisionMessage(String number);

}
