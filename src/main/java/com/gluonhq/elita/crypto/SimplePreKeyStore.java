/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita.crypto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

/**
 *
 * @author johan
 */
public class SimplePreKeyStore implements PreKeyStore, SignedPreKeyStore {

    Map<Integer, PreKeyRecord> map = new HashMap<>();
    Map<Integer, SignedPreKeyRecord> signedMap = new HashMap<>();
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
    
}
