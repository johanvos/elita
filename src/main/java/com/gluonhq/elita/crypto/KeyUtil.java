/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.libsignal.util.Medium;

/**
 *
 * @author johan
 */
public class KeyUtil {

    private static ECKeyPair identityKeyPair;
    private static SimplePreKeyStore preKeyStore = new SimplePreKeyStore();

    public synchronized static List<PreKeyRecord> generatePreKeys(int cnt) {
        List<PreKeyRecord> records = new LinkedList<>();
        int preKeyIdOffset = 1; // TODO 

        for (int i = 0; i < cnt; i++) {
            int preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            preKeyStore.storePreKey(preKeyId, record);
            records.add(record);
        }
        return records;
    }

    private static int activeSignedPreKeyId = 1;
    private static int nextSignedPreKeyId = 2;

    static int getNextSignedPreKeyId() {
        return nextSignedPreKeyId;
    }

    static void setNextSignedPreKeyId(int v) {
        nextSignedPreKeyId = v;
    }

    static void setActiveSignedPreKeyId(int v) {
        activeSignedPreKeyId = v;
    }

    public synchronized static SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair, boolean active) {
        try {
            SimplePreKeyStore signedPreKeyStore = preKeyStore;
            int signedPreKeyId = getNextSignedPreKeyId();
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
            setNextSignedPreKeyId((signedPreKeyId + 1) % Medium.MAX_VALUE);

            if (active) {
                setActiveSignedPreKeyId(signedPreKeyId);
            }

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }
    
    public static void setIdentityKeyPair(ECKeyPair k) {
        identityKeyPair = k;
    }
    
    public static IdentityKeyPair getIdentityKeyPair() {
        
        return new IdentityKeyPair(new IdentityKey(identityKeyPair.getPublicKey()),
                               identityKeyPair.getPrivateKey());
//
//          IdentityKey  publicKey  = getIdentityKey();
//      ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(retrieve(IDENTITY_PRIVATE_KEY_PREF)));
//
//      return new IdentityKeyPair(publicKey, privateKey);
    }


}
