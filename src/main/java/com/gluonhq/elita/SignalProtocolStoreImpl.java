package com.gluonhq.elita;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceProtocolStore;

/**
 *
 * @author johan
 */
public class SignalProtocolStoreImpl implements SignalServiceProtocolStore {

    private IdentityKeyPair identityKeyPair;
    Map<Integer, PreKeyRecord> map = new HashMap<>();
    Map<Integer, SignedPreKeyRecord> signedMap = new HashMap<>();
    
    private Map<SignalProtocolAddress, byte[]> sessions = new HashMap<>();
    private final Map<SignalProtocolAddress, IdentityKey> trustedKeys = new HashMap<>();

    private int localRegistrationId;
    
    private int deviceId;
    private String myUuid = "nobody";

    public SignalProtocolStoreImpl() {}
    
    public SignalProtocolStoreImpl(IdentityKeyPair ikp, int registrationId, int deviceId) {
        this.identityKeyPair = ikp;
        this.localRegistrationId = registrationId;
        this.deviceId = deviceId;
    }
    
    public void setIdentityKeyPair(IdentityKeyPair ikp) {
        this.identityKeyPair = ikp;
    }
    
    public void setMyUuid(String v) {
        this.myUuid = v;
    }
    
    public String getMyUuid() {
        return this.myUuid;
    }
    
    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return this.identityKeyPair;
    }
    
    public void setDeviceId(int devid) {
        this.deviceId = devid;
    }
    
    public void setRegistrationId(int regid) {
        this.localRegistrationId = regid;
    }

    @Override
    public int getLocalRegistrationId() {
        return this.localRegistrationId;
    }
    
  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    IdentityKey existing = trustedKeys.get(address);

    if (!identityKey.equals(existing)) {
      trustedKeys.put(address, identityKey);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    IdentityKey trusted = trustedKeys.get(address);
    return (trusted == null || trusted.equals(identityKey));
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    return trustedKeys.get(address);
  }

      @Override
    public PreKeyRecord loadPreKey(int i) throws InvalidKeyIdException {
          System.err.println("[STORE] loadPreKey "+i);
        return map.get(i);
    }

    @Override
    public void storePreKey(int i, PreKeyRecord pkr) {
        System.err.println("[STORE] storePreKey "+i);
        map.put(i, pkr);
    }

    @Override
    public boolean containsPreKey(int i) {
        System.err.println("[STORE] containsPreKey? "+i);
        return map.containsKey(i);
    }

    @Override
    public void removePreKey(int i) {
        System.err.println("[STORE] removePreKey "+i);
        map.remove(i);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int i) throws InvalidKeyIdException {
        Thread.dumpStack();
        System.err.println("[STORE] load signed prekey "+i);
        return signedMap.get(i);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        System.err.println("[STORE] loadSignedPreKeys");
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void storeSignedPreKey(int i, SignedPreKeyRecord spkr) {
        Thread.dumpStack();
        System.err.println("[STORE] store signed prekey "+i);
        signedMap.put(i, spkr);
    }

    @Override
    public boolean containsSignedPreKey(int i) {
                Thread.dumpStack();
        System.err.println("[STORE] contains signed prekey? "+i);
        return signedMap.containsKey(i);
    }

    @Override
    public void removeSignedPreKey(int i) {
        Thread.dumpStack();
        System.err.println("[STORE] remove signed prekey? "+i);
        signedMap.remove(i);
    }
    
  @Override
  public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
    try {
      if (containsSession(remoteAddress)) {
          System.err.println("Load session for "+remoteAddress);
        return new SessionRecord(sessions.get(remoteAddress));
      } else {
          System.err.println("Load session for "+remoteAddress+" will create a new one");
        return new SessionRecord();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public synchronized List<Integer> getSubDeviceSessions(String name) {
    List<Integer> deviceIds = new LinkedList<>();
      System.err.println("getSubDeviceSessionas asked, myuuid = "+myUuid+" and devid = "+deviceId);
        for (SignalProtocolAddress key : sessions.keySet()) {
            if (key.getName().equals(name)
                    && !((key.getName().equals(myUuid)) && (key.getDeviceId() == deviceId))) {
                deviceIds.add(key.getDeviceId());
            } else {
                System.err.println("[getSubDeviceSessions] ignore " + key.getName() + " with id " + key.getDeviceId());
            }
        }
        System.err.println("SUBDEVICES asked for " + name + ", return " + deviceIds);
        return deviceIds;
    }
  
    @Override
    public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
      //Thread.dumpStack();
        System.err.println("[storeSession] with address "+address.getName()+" and id "+address.getDeviceId());
        sessions.put(address, record.serialize());
    }

  @Override
  public synchronized boolean containsSession(SignalProtocolAddress address) {
      boolean answer = sessions.containsKey(address);
    //  Thread.dumpStack();
      System.err.println("[STORE] containsSession asked for "+address+", return "+answer);
    return answer;
  }

  @Override
  public synchronized void deleteSession(SignalProtocolAddress address) {
    sessions.remove(address);
  }

  @Override
  public synchronized void deleteAllSessions(String name) {
    for (SignalProtocolAddress key : sessions.keySet()) {
      if (key.getName().equals(name)) {
        sessions.remove(key);
      }
    }
  }

    @Override
    public void archiveSession(SignalProtocolAddress address) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
