/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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

    public SignalProtocolStoreImpl() {}
    
    public SignalProtocolStoreImpl(IdentityKeyPair ikp, int registrationId) {
        this.identityKeyPair = ikp;
        this.localRegistrationId = registrationId;
        System.err.println("[SPSI] <init>");
    }
    
    public void setIdentityKeyPair(IdentityKeyPair ikp) {
        this.identityKeyPair = ikp;
    }
    
    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return this.identityKeyPair;
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
        return map.get(i);
    }

    @Override
    public void storePreKey(int i, PreKeyRecord pkr) {
        map.put(i, pkr);
    }

    @Override
    public boolean containsPreKey(int i) {
        return map.containsKey(i);
    }

    @Override
    public void removePreKey(int i) {
        map.remove(i);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int i) throws InvalidKeyIdException {
        return signedMap.get(i);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void storeSignedPreKey(int i, SignedPreKeyRecord spkr) {
        signedMap.put(i, spkr);
    }

    @Override
    public boolean containsSignedPreKey(int i) {
        return signedMap.containsKey(i);
    }

    @Override
    public void removeSignedPreKey(int i) {
        signedMap.remove(i);
    }
    
  @Override
  public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
    try {
      if (containsSession(remoteAddress)) {
        return new SessionRecord(sessions.get(remoteAddress));
      } else {
        return new SessionRecord();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public synchronized List<Integer> getSubDeviceSessions(String name) {
    List<Integer> deviceIds = new LinkedList<>();

    for (SignalProtocolAddress key : sessions.keySet()) {
      if (key.getName().equals(name) &&
          key.getDeviceId() != 1)
      {
        deviceIds.add(key.getDeviceId());
      }
    }
      System.err.println("SUBDEVICES asked for "+name+", return "+ deviceIds);
    return deviceIds;
  }
  
  @Override
  public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
    sessions.put(address, record.serialize());
  }

  @Override
  public synchronized boolean containsSession(SignalProtocolAddress address) {
    return sessions.containsKey(address);
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
