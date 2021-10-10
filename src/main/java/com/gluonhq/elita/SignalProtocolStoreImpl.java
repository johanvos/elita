package com.gluonhq.elita;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceProtocolStore;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

/**
 *
 * @author johan
 */
public class SignalProtocolStoreImpl implements SignalServiceProtocolStore {

    private IdentityKeyPair identityKeyPair;
    Map<Integer, PreKeyRecord> map = new HashMap<>();
    Map<Integer, SignedPreKeyRecord> signedMap = new HashMap<>();

    private StaticCredentialsProvider credentialsProvider;

    private boolean initialized;

    private Map<SignalProtocolAddress, byte[]> sessions = new HashMap<>();
    private final Map<SignalProtocolAddress, IdentityKey> trustedKeys = new HashMap<>();

    private int localRegistrationId;

    private int deviceId;
    private String myUuid = "nobody";

    private final static String SIGNAL_FX;
    public final static Path SIGNAL_FX_PATH;
    public final static Path SIGNAL_FX_STORE_PATH;
    private final static File SIGNAL_FX_DIR;
    public final static File SIGNAL_FX_CONTACTS_DIR;

    static {
        SIGNAL_FX = System.getProperty("user.home")
                + File.separator + ".signalfx";
        SIGNAL_FX_DIR = new File(SIGNAL_FX);
        SIGNAL_FX_DIR.mkdirs();
        SIGNAL_FX_PATH = SIGNAL_FX_DIR.toPath();
        SIGNAL_FX_STORE_PATH = SIGNAL_FX_PATH.resolve("store");
        Path contacts = SIGNAL_FX_DIR.toPath().resolve("contacts/");
        SIGNAL_FX_CONTACTS_DIR = contacts.toFile();
        try {
            Files.createDirectories(SIGNAL_FX_STORE_PATH);
            Files.createDirectories(contacts);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static SignalProtocolStoreImpl instance = new SignalProtocolStoreImpl();

    public static SignalProtocolStoreImpl getInstance() {
        return instance;
    }

    private SignalProtocolStoreImpl() {
        // if we have a credentialsprovider, we assume we are initialized, and
        // the other stored info is retrieved.
        this.initialized = retrieveCredentialsProvider();
        if (this.initialized) {
            try {
                retrieveIdentityKeyPair();
                retrieveSignedPreKeys();
                retrievePreKeys();
            } catch (IOException ex) {
                ex.printStackTrace();
                Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

//    public SignalProtocolStoreImpl(IdentityKeyPair ikp, int registrationId, int deviceId) {
//        this.identityKeyPair = ikp;
//        this.localRegistrationId = registrationId;
//        this.deviceId = deviceId;
//    }
//    
    /**
     * Checks if the store is initialized. Once it has a keypair in persistent
     * storage, we assume it is initialized.
     *
     * @return
     */
    public boolean isInitialized() {
        return initialized;
    }

    public void setIdentityKeyPair(IdentityKeyPair ikp) {
        System.err.println("SPSI setIdentityKeyPair");
        this.identityKeyPair = ikp;
        persistIdentityKeyPair();
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
        System.err.println("[STORE] loadPreKey " + i);
        return map.get(i);
    }

    @Override
    public void storePreKey(int i, PreKeyRecord pkr) {
        System.err.println("[STORE] storePreKey " + i);
        map.put(i, pkr);
        try {
            persistPreKey(i, pkr);
        } catch (IOException ex) {
            Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public boolean containsPreKey(int i) {
        System.err.println("[STORE] containsPreKey? " + i);
        return map.containsKey(i);
    }

    @Override
    public void removePreKey(int i) {
        System.err.println("[STORE] removePreKey " + i);
        map.remove(i);
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int i) throws InvalidKeyIdException {
        Thread.dumpStack();
        System.err.println("[STORE] load signed prekey " + i);
        return signedMap.get(i);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        System.err.println("[LOAD] loadSignedPreKeys");
        List<SignedPreKeyRecord> answer = new ArrayList<>(signedMap.size());
        answer.addAll(signedMap.values());
        return answer;
    }

    @Override
    public void storeSignedPreKey(int i, SignedPreKeyRecord spkr) {
        Thread.dumpStack();
        System.err.println("[STORE] store signed prekey " + i);
        signedMap.put(i, spkr);
        persistSignedPreKeys();
    }

    @Override
    public boolean containsSignedPreKey(int i) {
        Thread.dumpStack();
        System.err.println("[STORE] contains signed prekey? " + i);
        return signedMap.containsKey(i);
    }

    @Override
    public void removeSignedPreKey(int i) {
        Thread.dumpStack();
        System.err.println("[STORE] remove signed prekey? " + i);
        signedMap.remove(i);
    }

    @Override
    public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
        try {
            if (containsSession(remoteAddress)) {
                System.err.println("Load session for " + remoteAddress);
                return new SessionRecord(sessions.get(remoteAddress));
            } else {
                System.err.println("Load session for " + remoteAddress + " will create a new one");
                return new SessionRecord();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public synchronized List<Integer> getSubDeviceSessions(String name) {
        List<Integer> deviceIds = new LinkedList<>();
        System.err.println("getSubDeviceSessionas asked, myuuid = " + myUuid + " and devid = " + deviceId);
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
        System.err.println("[storeSession] with address " + address.getName() + " and id " + address.getDeviceId());
        sessions.put(address, record.serialize());
    }

    @Override
    public synchronized boolean containsSession(SignalProtocolAddress address) {
        boolean answer = sessions.containsKey(address);
        //  Thread.dumpStack();
        System.err.println("[STORE] containsSession asked for " + address + ", return " + answer);
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

    public void setCredentialsProvider(StaticCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        this.myUuid = credentialsProvider.getUuid().toString();
        this.deviceId = credentialsProvider.getDeviceId();
        persistCredentialsProvider();
    }

    public StaticCredentialsProvider getCredentialsProvider() {
        return this.credentialsProvider;
    }

    /**
     * Save the byes for this keypair to persistent storage
     *
     * @param b
     * @throws InvalidKeyException
     */
//    private void saveIdentityKeyPair(byte[] b) throws InvalidKeyException {
//        Thread.dumpStack();
//        String dirname = System.getProperty("user.home")
//                + File.separator + ".signalfx";
//        File dir = new File(dirname);
//        dir.mkdirs();
//        Path path = dir.toPath().resolve("keypair");
//        try {
//            Files.write(path, b, StandardOpenOption.CREATE);
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
//    }
    private void persistPreKeys() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void persistCredentialsProvider() {
        try {
            Path path = SIGNAL_FX_STORE_PATH.resolve("credentials");
            File credFile = path.toFile();
            if (credFile.exists()) {
                credFile.delete();
            }
            UUID uuid = credentialsProvider.getUuid();
            Files.writeString(path, uuid.toString() + "\n", StandardOpenOption.CREATE);
            Files.writeString(path, credentialsProvider.getE164() + "\n", StandardOpenOption.APPEND);
            Files.writeString(path, credentialsProvider.getPassword() + "\n", StandardOpenOption.APPEND);
            Files.writeString(path, Integer.toString(credentialsProvider.getDeviceId()) + "\n", StandardOpenOption.APPEND);
            Files.writeString(path, credentialsProvider.getSignalingKey() + "\n", StandardOpenOption.APPEND);
        } catch (IOException ex) {
            Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean retrieveCredentialsProvider() {
        try {
            Path path = SIGNAL_FX_STORE_PATH.resolve("credentials");
            if (!Files.exists(path)) {
                return false;
            }
            List<String> lines = Files.readAllLines(path);
            String uuidString = lines.get(0);
            UUID uuid = UUID.fromString(uuidString);
            String number = lines.get(1);
            String password = lines.get(2);
            this.deviceId = Integer.parseInt(lines.get(3));
            this.myUuid = uuidString;

            this.credentialsProvider = new StaticCredentialsProvider(uuid,
                    number, password, "signalingkey", deviceId);
            return true;

        } catch (IOException ex) {
            Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private void persistIdentityKeyPair() {
        Thread.dumpStack();
        Path path = SIGNAL_FX_STORE_PATH.resolve("identity");
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        byte[] b = identityKeyPair.serialize();
        try {
            Files.write(path, b);
        } catch (IOException ex) {
            ex.printStackTrace();
            Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean retrieveIdentityKeyPair() throws IOException {
        try {
            Thread.dumpStack();
            Path path = SIGNAL_FX_STORE_PATH.resolve("identity");
            File f = path.toFile();
            byte[] b = Files.readAllBytes(path);
            this.identityKeyPair = new IdentityKeyPair(b);
            return true;
        } catch (InvalidKeyException ex) {
            ex.printStackTrace();
            Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private void persistPreKey(int i, PreKeyRecord pkr) throws IOException {
        Thread.dumpStack();
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("prekeys");
        if (!Files.exists(ppath)) {
            Files.createDirectories(ppath);
        }
        Path path = ppath.resolve(Integer.toString(i));
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        Files.write(path, pkr.serialize());
    }

    private boolean retrievePreKeys() throws IOException {
        Thread.dumpStack();
        Path ppath = SIGNAL_FX_STORE_PATH.resolve("prekeys");
        if (!Files.exists(ppath)) {
            Files.createDirectories(ppath);
        }
        Files.list(ppath).forEach(path -> {
            try {
                String name = path.getFileName().toString();
                int i = Integer.parseInt(name);
                byte[] b = Files.readAllBytes(path);
                map.put(i, new PreKeyRecord(b));
            } catch (IOException ex) {
                ex.printStackTrace();
                Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return true;
    }

    private void persistSignedPreKeys() {
        Thread.dumpStack();
        Path path = SIGNAL_FX_STORE_PATH.resolve("signedprekeys");
        File f = path.toFile();
        if (f.exists()) {
            f.delete();
        }
        List<String> lines = new LinkedList<String>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream daos = new DataOutputStream(baos);
        try {
            daos.writeInt(signedMap.size());
            for (Entry<Integer, SignedPreKeyRecord> entry : signedMap.entrySet()) {
                lines.add(Integer.toString(entry.getKey()));
                byte[] b = entry.getValue().serialize();
                daos.writeInt(entry.getKey());
                daos.writeInt(b.length);
                daos.write(b);
                System.err.println("Store SIGNEDPREKEY with id " + entry.getKey() + " and " + b.length + " bytes");
                System.err.println("bytes = " + Arrays.toString(b));
//                lines.add(new String(b));
            }
            daos.flush();
            Files.write(path, baos.toByteArray());
        } catch (IOException ex) {
            Logger.getLogger(SignalProtocolStoreImpl.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private boolean retrieveSignedPreKeys() throws IOException {
        Thread.dumpStack();
        Path path = SIGNAL_FX_STORE_PATH.resolve("signedprekeys");
        byte[] b = Files.readAllBytes(path);
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        DataInputStream dis = new DataInputStream(bais);
        int entriesize = dis.readInt();
        signedMap.clear();
        System.err.println("retrieving signed PK's, size = " + entriesize);
        for (int i = 0; i < entriesize; i++) {
            int id = dis.readInt();
            int bs = dis.readInt();
            byte[] spkrb = new byte[bs];
            int read = dis.read(spkrb);
            if (read != bs) {
                throw new RuntimeException("signed prekeys tampered with!");
            }
            System.err.println("got id " + id + " with " + b.length + " bytes");
            System.err.println("PK = " + Arrays.toString(b));
            SignedPreKeyRecord spkr = new SignedPreKeyRecord(spkrb);
            signedMap.put(id, spkr);
        }
        return true;

    }

}
