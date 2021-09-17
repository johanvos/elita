/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import java.util.HashMap;
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

/**
 *
 * @author johan
 */
public class SignalProtocolStoreImpl implements SignalProtocolStore {

    private IdentityKeyPair identityKeyPair;
    Map<Integer, PreKeyRecord> map = new HashMap<>();
    Map<Integer, SignedPreKeyRecord> signedMap = new HashMap<>();

    public SignalProtocolStoreImpl() {
        System.err.println("[SPSI] <init>");
    }
    
    public void setIdentityKeyPair(IdentityKeyPair ikp) {
        this.identityKeyPair = ikp;
    }
    
    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return this.identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress spa, IdentityKey ik) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress spa, IdentityKey ik, Direction drctn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress spa) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
    public SessionRecord loadSession(SignalProtocolAddress spa) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Integer> getSubDeviceSessions(String string) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void storeSession(SignalProtocolAddress spa, SessionRecord sr) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean containsSession(SignalProtocolAddress spa) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteSession(SignalProtocolAddress spa) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteAllSessions(String string) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
