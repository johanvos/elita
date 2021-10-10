/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita.crypto;

import com.gluonhq.elita.SignalProtocolStoreImpl;

import java.util.LinkedList;
import java.util.List;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;

/**
 *
 * @author johan
 */
public class KeyUtil {

    private static SignalProtocolStore protocolStore;

    public static void setSignalProtocolStore (SignalProtocolStoreImpl p) {
        protocolStore = p;
    }
    public synchronized static List<PreKeyRecord> generatePreKeys(int cnt) {
        List<PreKeyRecord> records = new LinkedList<>();
        int preKeyIdOffset = 1; // TODO 

        for (int i = 0; i < cnt; i++) {
            int preKeyId = (preKeyIdOffset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            protocolStore.storePreKey(preKeyId, record);
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
            int signedPreKeyId = getNextSignedPreKeyId();
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            protocolStore.storeSignedPreKey(signedPreKeyId, record);
            setNextSignedPreKeyId((signedPreKeyId + 1) % Medium.MAX_VALUE);

            if (active) {
                setActiveSignedPreKeyId(signedPreKeyId);
            }

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }
 
}
