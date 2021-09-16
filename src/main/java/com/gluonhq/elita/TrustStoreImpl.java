package com.gluonhq.elita;

import java.io.InputStream;
import org.whispersystems.signalservice.api.push.TrustStore;

/**
 *
 * @author johan
 */
public class TrustStoreImpl implements TrustStore {

    @Override
    public InputStream getKeyStoreInputStream() {
     //   return TrustStoreImpl.class.getResourceAsStream("/censorship_digicert.store");
     //   return TrustStoreImpl.class.getResourceAsStream("/censorship_fronting.store");
        return TrustStoreImpl.class.getResourceAsStream("/whisper.store");
    }

    @Override
    public String getKeyStorePassword() {
        return "whisper";
    }
    
}
