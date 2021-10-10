package com.gluonhq.wave;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gluonhq.elita.crypto.KeyUtil;
import com.gluonhq.elita.LockImpl;
import com.gluonhq.elita.SignalProtocolStoreImpl;
import static com.gluonhq.elita.SignalProtocolStoreImpl.SIGNAL_FX_CONTACTS_DIR;
import com.gluonhq.elita.TrustStoreImpl;
import com.gluonhq.wave.message.MessagingClient;
import com.gluonhq.wave.provisioning.ProvisioningClient;
import com.gluonhq.wave.provisioning.ProvisioningManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import okhttp3.Interceptor;
import okhttp3.Response;
//import org.eclipse.jetty.client.api.ContentResponse;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.ProtocolNoSessionException;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalKeyBackupServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.util.Base64;

/**
 *
 * @author johan
 */
public class WaveManager {

    private ProvisioningManager provisioningManager;

    static final String SIGNAL_SERVICE_URL = "https://textsecure-service.whispersystems.org";
    static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";
    static final String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
    static final String SIGNAL_STORAGE_URL = "https://storage.signal.org";
    static final String UNIDENTIFIED_SENDER_TRUST_ROOT = "BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF";
    long MAX_FILE_STORAGE = 1024 * 1024 * 4;
    static final TrustStore trustStore = new TrustStoreImpl();
    final SignalServiceConfiguration signalServiceConfiguration;

    private final SignalProtocolStoreImpl signalProtocolStore;

    private CredentialsProvider credentialsProvider;
    private LockImpl lock;
    private SignalServiceAddress signalServiceAddress;
    private SignalServiceMessageReceiver receiver;
    private SignalServiceMessageSender sender;
    private boolean connected;

    public final static File SIGNAL_FX_CONTACTS_DIR;
    
    static {
        Path contacts = SignalProtocolStoreImpl.SIGNAL_FX_PATH.resolve("contacts/");
        SIGNAL_FX_CONTACTS_DIR = contacts.toFile();
        try {
            Files.createDirectories(contacts);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
//
//    static {
//        SIGNAL_FX = System.getProperty("user.home")
//                + File.separator + ".signalfx";
//        SIGNAL_FX_DIR = new File(SIGNAL_FX);
//        SIGNAL_FX_DIR.mkdirs();
//        Path contacts = SIGNAL_FX_DIR.toPath().resolve("contacts/");
//        SIGNAL_FX_CONTACTS_DIR = contacts.toFile();
//        try {
//            Files.createDirectories(contacts);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }

    private MessagingClient messageListener;
    private static WaveManager INSTANCE = new WaveManager();
    SignalServiceMessagePipe messagePipe;
    
    private WaveManager() {
        signalServiceConfiguration = createConfiguration();
        signalProtocolStore = SignalProtocolStoreImpl.getInstance();
        credentialsProvider = signalProtocolStore.getCredentialsProvider();
        KeyUtil.setSignalProtocolStore(signalProtocolStore);
        lock = new LockImpl();
        if (isInitialized()) {
            this.signalServiceAddress = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
        }
//        contacts.addListener(new InvalidationListener() {
//            @Override
//            public void invalidated(Observable o) {
//                System.err.println("WAVEMANAGER, contacts invalidated! "+ contacts.toString());
//            }
//        });
//        try {
//            restoreCredentialsProvider();
//            retrieveIdentityKeyPair();
//            retrieveSignedPreKey();
//        } catch (IOException e) {
//            System.err.println("[WaveManager] no credentials found!");
//        } catch (InvalidKeyException ex) {
//            Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }
    
    public static WaveManager getInstance() {
        return INSTANCE;
    }

    public void initalize() {
        if (!isInitialized()) {
        }
    }

    public boolean isInitialized() {
        return signalProtocolStore.isInitialized();
    }

    public String getMyUuid () {
        return this.credentialsProvider.getUuid().toString();
    }
    
    public void startProvisioning(ProvisioningClient provisioningClient) {
        provisioningManager = new ProvisioningManager(this, provisioningClient);
        provisioningManager.start();
    }

    public void createAccount(String nr, String deviceName) throws JsonProcessingException, IOException {
        provisioningManager.createAccount(nr, deviceName);
        this.credentialsProvider = signalProtocolStore.getCredentialsProvider();
        this.signalServiceAddress = new SignalServiceAddress(credentialsProvider.getUuid(), credentialsProvider.getE164());
        provisioningManager.stop();
    }
    
    // MESSAGES
    public void setMessageListener(MessagingClient mc) {
        this.messageListener = mc;
    }

    // CONTACTS
    private final ObservableList<Contact> contacts = FXCollections.observableArrayList();
    private boolean contactStorageDirty = true;

    public ObservableList<Contact> getContacts() {
        System.err.println("getContacts asked, csd = "+contactStorageDirty+
                " and current size = "+contacts.size()+" and contacts = "+
                System.identityHashCode(contacts)+" and wave = "+System.identityHashCode(this));
        if (contactStorageDirty) {
            try {
                contacts.clear(); // TODO make this smarter
                contacts.addAll(readContacts());
                contactStorageDirty = false;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return contacts;
    }
    
    // read contacts from file
    private List<Contact> readContacts() throws IOException {
        List<Contact> answer = new LinkedList<>();
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("contactlist");
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i = i + 4) {
                Contact c = new Contact(lines.get(i), lines.get(i + 1), lines.get(i + 2));
                String avt = lines.get(i + 3);
                c.setAvatarPath(avt);
                answer.add(c);
            }
        }
        return answer;
    }
    
    private void storeContacts() throws IOException {
        Path path = SIGNAL_FX_CONTACTS_DIR.toPath().resolve("contactlist");
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        List<String> lines = new LinkedList<String>();
        for (Contact contact : contacts) {
            lines.add(contact.getName());
            lines.add(contact.getUuid());
            lines.add(contact.getNr());
            lines.add(contact.getAvatarPath());
        }
        Files.write(path, lines);
        contactStorageDirty = true;
      //  getContacts();
    }

    public void syncContacts() throws IOException, UntrustedIdentityException, InvalidKeyException {
        ensureConnected();
        System.err.println("SYNC CONTACTS");
        Thread.dumpStack();
        SignalServiceProtos.SyncMessage.Request request = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        System.err.println("[WAVEMANAGER] will now send SyncContacts request");
        sender.sendMessage(message, Optional.empty());
        System.err.println("[WAVEMANAGER] did send SyncContacts request");

    }
    
    public void fetchMissedMessages() throws IOException, UntrustedIdentityException {
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE);
        sender.sendMessage(message, Optional.empty());
    }
    
    public void sendMessage(String uuid, String text) {
        ensureConnected();
        Contact target = contacts.stream().filter(c -> uuid.equals(c.getUuid())).findFirst().get();

        Optional<SignalServiceAddress> add = SignalServiceAddress.fromRaw(uuid, target.getNr());
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder().withBody(text).build();
        try {
            sender.sendMessage(add.get(), Optional.empty(), message);
        } catch (UntrustedIdentityException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
//
//    static Path getCredentialsPath() {
////        String dirname = System.getProperty("user.home")
////                + File.separator + ".signalfx";
//        File dir = new File(SIGNAL_FX);
////        dir.mkdirs();
//        Path path = dir.toPath().resolve("credentials");
//        return path;
//    }

//    
//    public void saveSignedPreKey(byte[] b) throws InvalidKeyException {
//        Thread.dumpStack();
//        Path path = SIGNAL_FX_DIR.toPath().resolve("signedprekey");
//        try {
//            Files.write(path, b, StandardOpenOption.CREATE);
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
//    }
//    
//    public SignedPreKeyRecord retrieveSignedPreKey() throws InvalidKeyException, IOException {
//        Thread.dumpStack();
//        Path path = SIGNAL_FX_DIR.toPath().resolve("signedprekey");
//        byte[] b = Files.readAllBytes(path);
//        SignedPreKeyRecord answer = new SignedPreKeyRecord(b);
//        this.signalProtocolStore.storeSignedPreKey(2, answer);
//        return answer;
//    }

    /**
     * Create the identityKeyPair based on the provided byte sequence and stores
     * it in the SignalProtocolStore (in memory)
     * @param b the byte sequence
     * @throws InvalidKeyException 
     */
    void storeIdentityKeyPair(byte[] b) throws InvalidKeyException {
        ECPrivateKey privateKey = Curve.decodePrivatePoint(b);
        ECPublicKey publicKey = Curve.createPublicKeyFromPrivateKey(b);
        ECKeyPair keyPair = new ECKeyPair(publicKey, privateKey);
        System.err.println("identitykp = " + keyPair);
        IdentityKey identityKey = new IdentityKey(publicKey);
        IdentityKeyPair ikp = new IdentityKeyPair(identityKey, privateKey);
        signalProtocolStore.setIdentityKeyPair(ikp);
    }

    /**
     * Retrieve the IdentityKeyPair from disk and set it on the 
     * SignalProtocolStore
     */
    void retrieveIdentityKeyPair() throws InvalidKeyException, IOException {
        Thread.dumpStack();
        String dirname = System.getProperty("user.home")
                + File.separator + ".signalfx";
        File dir = new File(dirname);
        dir.mkdirs();
        Path path = dir.toPath().resolve("keypair");
        byte[] b = Files.readAllBytes(path);
        storeIdentityKeyPair(b);
    }

    public void ensureConnected() {
        if (connected) {
            return;
        }
        connect();
    }

    public void connect() {
        System.err.println("[CLIENT] create receiver");
        this.receiver = createMessageReceiver();
        System.err.println("[CLIENT] created receiver, wait a bit");
        this.sender = createMessageSender(receiver);
        this.connected = true;
        System.err.println("Created sender");
    }

    public void startListening() {
        System.err.println("[WM] startListening");
        try {

//            List<SignalServiceEnvelope> retrieved = this.receiver.retrieveMessages(e -> {
//                try {
//                    System.err.println("WM, retrieveMessage callback! Will process env "+Objects.hashCode(e));
//                    processEnvelope(e);
//                } catch (Exception ex) {
//                    Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            });
//            System.err.println("asked to retrieve messages, result = " + retrieved);
            processMessagePipe(messagePipe);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private SignalServiceMessageReceiver createMessageReceiver() {
        // ensure configuration and provider are ok
        assert (signalServiceConfiguration != null);
        assert (credentialsProvider != null);
        if (signalServiceConfiguration == null) {
            throw new IllegalArgumentException("no signalserviceconfiguration");
        }
        if (credentialsProvider == null) {
            throw new IllegalArgumentException("no credentialsProvider");
        }
        ConnectivityListener cl = new ClientConnectivityListener();
        SleepTimer sleepTimer = m -> Thread.sleep(m);
        SignalServiceMessageReceiver answer = new SignalServiceMessageReceiver(
                signalServiceConfiguration,
                credentialsProvider,
                SIGNAL_USER_AGENT,
                cl,
                sleepTimer,
                null,
                true);
        return answer;
    }

    private SignalServiceMessageSender createMessageSender(SignalServiceMessageReceiver receiver) {
        messagePipe = receiver.createMessagePipe();
        SignalServiceMessagePipe unidentifiedMessagePipe = receiver.createUnidentifiedMessagePipe();
        ExecutorService executorService = new ScheduledThreadPoolExecutor(5);
        SignalServiceMessageSender sender = new SignalServiceMessageSender(
                signalServiceConfiguration,
                credentialsProvider,
                signalProtocolStore,
                lock,
                SIGNAL_USER_AGENT,
                true,
                Optional.of(messagePipe),
                Optional.of(unidentifiedMessagePipe),
                Optional.empty(),
                null,
                executorService,
                512 * 1024,
                true);
        return sender;
    }

    void processMessagePipe(SignalServiceMessagePipe pipe) {
//        Thread t0 = new Thread() {
//            @Override
//            public void run() {
//                boolean backlog = true;
//                while (backlog) {
//                    try {
//                        System.err.println("Waiting to process backlog entry");
//                        SignalServiceEnvelope env = pipe.read(10, TimeUnit.SECONDS, new SignalServiceMessagePipe.MessagePipeCallback() {
//                            @Override
//                            public void onMessage(SignalServiceEnvelope envelope) {
//                                System.err.println("CALLBACK got message, env = " + envelope);
//                                try {
//                                    processEnvelope(envelope);
//                                } catch (Exception ex) {
//                                    Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
//                                }
//                            }
//                        });
//                        System.err.println("Got backlog entry? "+env);
//                        
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                        backlog = false;
//                    }
//                }
//            }
//        };
//        t0.start();

        Thread t = new Thread() {
            @Override
            public void run() {
                boolean listen = true;
                while (listen) {
                    try {
                        System.err.println("[PIPE] waiting for envelope...");
                        SignalServiceEnvelope envelope = pipe.read(20, TimeUnit.SECONDS);
                        System.err.println("[PIPE] got envelope "+Objects.hashCode(envelope));
                        System.err.println("[PIPE] envelope Type = " + envelope.getType());
                        SignalServiceContent content = mydecrypt(envelope);
                        System.err.println("[PIPE] got content: " + content);
                        if (content != null) {
                            if (content.getSyncMessage().isPresent()) {
                                System.err.println("[PIPE] envelope has syncmessage");
                                SignalServiceSyncMessage sssm = content.getSyncMessage().get();
                                processSyncMessage(sssm);
                            }
                            if (content.getDataMessage().isPresent()) {
                                SignalServiceDataMessage ssdm = content.getDataMessage().get();
                                processDataMessage(content.getSender(), ssdm);
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };
        t.start();
    }

    private void processEnvelope(SignalServiceEnvelope envelope) throws Exception {
        System.err.println("WaveManager will process Envelope " + envelope);
        SignalServiceContent content = mydecrypt(envelope);
        System.err.println("[WM] got content: " + content);
        if (content != null) {
            if (content.getSyncMessage().isPresent()) {
                System.err.println("[WM] envelope has syncmessage");
                SignalServiceSyncMessage sssm = content.getSyncMessage().get();
                processSyncMessage(sssm);
            }
            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage ssdm = content.getDataMessage().get();
                processDataMessage(content.getSender(), ssdm);
            }
        }
    }
    
    SignalServiceContent mydecrypt(SignalServiceEnvelope sse) throws Exception {
        SignalServiceCipher cipher = new SignalServiceCipher(signalServiceAddress,
                signalProtocolStore,
                new LockImpl(),
                getCertificateValidator());
        SignalServiceContent content = null;
        try {
            int bl = sse.getContent().length;
            System.err.println("[WM] I need to decrypt " + sse+" with " +bl+" bytes, with id "+Objects.hashCode(sse));
            content = cipher.decrypt(sse);
            System.err.println("[WM] I did to decrypt " + sse+" with id "+Objects.hashCode(sse));
        } catch (ProtocolNoSessionException e) {
            System.err.println("ProtocolNoSessionException!");
            String senderId = e.getSender();
            int senderDevice = e.getSenderDevice();
            System.err.println("SENDERID = " + senderId + " and dev = " + senderDevice);
            String tuuid = null;
            if (this.signalProtocolStore.getMyUuid().equals(senderId)) {
                System.err.println("It's me!");
                tuuid = senderId;
                senderId = "";
            }
            if (tuuid == null) {
                tuuid = getContactByNumber(senderId).get().getUuid();
            }
            SignalServiceAddress addy
                    = new SignalServiceAddress(UUID.fromString(tuuid), senderId);
            System.err.println("[WM] decrypt will send nullmessage to "+addy);
            sender.sendNullMessage(addy, Optional.empty());
            int bl2 = sse.getContent().length;
            System.err.println("[WM] did send null message, we should have session now for "+bl2+" bytes");

            SignalServiceCipher cipher2 = new SignalServiceCipher(signalServiceAddress,
                signalProtocolStore,
                new LockImpl(),
                getCertificateValidator());
            content = cipher2.decrypt(sse);
        }
        System.err.println("[WM] descrypt will return "+content);
        return content;
    }

    void processSyncMessage(SignalServiceSyncMessage sssm) throws InvalidMessageException, IOException {
        System.err.println("Process SyncMessage!");
        if (sssm.getContacts().isPresent()) {
            ContactsMessage msg = sssm.getContacts().get();
            processContactsMessage(msg);
        }
    }
    
    void processDataMessage(SignalServiceAddress ssa, SignalServiceDataMessage ssdm) {
        System.err.println("Process datamessage");
        if (this.messageListener != null) {
            String uuid = ssa.getUuid().get().toString();
            String content = ssdm.getBody().get();
            this.messageListener.gotMessage(uuid, content, ssdm.getTimestamp());
        }
        
    }
    
    private void processContactsMessage(ContactsMessage msg) throws IOException {
        SignalServiceAttachment att = msg.getContactsStream();
        SignalServiceAttachmentPointer pointer = att.asPointer();
        Path output = Files.createTempFile("pre", "post");

        try {
            receiver.retrieveAttachment(pointer, output.toFile(), MAX_FILE_STORAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IOException("Can't retrieve attachment", ex);
        }
//        
//        int cdnNumber = pointer.getCdnNumber();
//        String path = new String(pointer.getRemoteId().getV3().get());
//        ContentResponse response = Networking.httpRequest("https://cdn2.signal.org", "GET", "/attachments/" + path, null, null);
//        Path output = Files.createTempFile("pre", "post");
//        Files.write(output, response.getContent());
//        
        
        try {
            InputStream ais = AttachmentCipherInputStream.createForAttachment(output.toFile(), pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get());
            Path attPath = Files.createTempFile("att", "bin");
            File attFile = attPath.toFile();
            Files.copy(ais, attPath, StandardCopyOption.REPLACE_EXISTING);

            File f = new File("/tmp/myin3");
            InputStream ois = new FileInputStream(attFile);
            DeviceContactsInputStream is = new DeviceContactsInputStream(ois);
            DeviceContact dc = is.read();

            while (dc != null) {
                System.err.println("Got contact: " + dc.getName() + ", uuid = " + dc.getAddress().getUuid()
                        + ", nr = " + dc.getAddress().getNumber());
                Contact contact = new Contact(dc.getName().orElse("anonymous"),
                        dc.getAddress().getUuid().get().toString(),
                        dc.getAddress().getNumber().orElse("123"));
                if (dc.getAvatar().isPresent()) {
                    SignalServiceAttachmentStream ssas = dc.getAvatar().get();
                    long length = ssas.getLength();
                    InputStream inputStream = ssas.getInputStream();
                    byte[] b = new byte[(int) length];
                    inputStream.read(b);
                    String nr = dc.getAddress().getNumber().get();
Path contacts = SIGNAL_FX_CONTACTS_DIR.toPath();
                    Path avatarPath = contacts.resolve("contact-avatar"+nr);
                    Files.write(avatarPath, b, StandardOpenOption.CREATE);
                    contact.setAvatarPath(avatarPath.toAbsolutePath().toString());
                }

                contacts.add(contact);
                System.err.println("Available? " + ois.available());
                if (ois.available() == 0) {
                    dc = null;
                } else {
                    dc = is.read();
                }
            }
            storeContacts();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println("Done reading/sync");

    }

    private static SignalServiceConfiguration createConfiguration() {
        SignalServiceUrl[] urls = {
            new SignalServiceUrl(SIGNAL_SERVICE_URL, trustStore)};
        Map<Integer, SignalCdnUrl[]> cdnMap = new HashMap<>();
        cdnMap.put(0, new SignalCdnUrl[]{new SignalCdnUrl("https://cdn.signal.org", trustStore)});
        cdnMap.put(2, new SignalCdnUrl[]{new SignalCdnUrl("https://cdn2.signal.org", trustStore)});
        SignalKeyBackupServiceUrl[] backup = new SignalKeyBackupServiceUrl[]{
            new SignalKeyBackupServiceUrl(SIGNAL_KEY_BACKUP_URL, trustStore)};

        SignalStorageUrl[] storageUrl = new SignalStorageUrl[]{
            new SignalStorageUrl(SIGNAL_STORAGE_URL, trustStore)};
        List<Interceptor> interceptors = new LinkedList<>();
        SignalServiceConfiguration answer = new SignalServiceConfiguration(
                urls, cdnMap,
                new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl("https://api.directory.signal.org", trustStore)},
                backup, storageUrl, interceptors,
                Optional.empty(), Optional.empty(), null
        );
        return answer;
    }
//
//    // get credentials info from storage and populate instance fields.
//    private void restoreCredentialsProvider() throws IOException {
//        Path path = getCredentialsPath();
//        List<String> lines = Files.readAllLines(path);
//        String uuidString = lines.get(0);
//        UUID uuid = UUID.fromString(uuidString);
//        String number = lines.get(1);
//        String password = lines.get(2);
//        int deviceId = Integer.parseInt(lines.get(3));
//        this.signalProtocolStore.setDeviceId(deviceId);
//        this.signalProtocolStore.setMyUuid(uuidString);
//
//        this.credentialsProvider = new StaticCredentialsProvider(uuid,
//                number, password, "signalingkey", deviceId);
//        this.signalServiceAddress = new SignalServiceAddress(uuid, number);
//
//    }
//
//    public void storeCredentialsProvider(CredentialsProvider cp) throws IOException {
//        this.credentialsProvider = cp;
//        if (cp == null) {
//            throw new IllegalArgumentException("No CredentialsProvider");
//        }
//        Path path = getCredentialsPath();
//        File credFile = path.toFile();
//        if (credFile.exists()) {
//            credFile.delete();
//        }
//
//        UUID uuid = cp.getUuid();
//
//        Files.writeString(path, uuid.toString() + "\n", StandardOpenOption.CREATE);
//        Files.writeString(path, cp.getE164() + "\n", StandardOpenOption.APPEND);
//        Files.writeString(path, cp.getPassword() + "\n", StandardOpenOption.APPEND);
//        Files.writeString(path, Integer.toString(cp.getDeviceId()) + "\n", StandardOpenOption.APPEND);
//        Files.writeString(path, cp.getSignalingKey() + "\n", StandardOpenOption.APPEND);
//        restoreCredentialsProvider();
//    }

    public SignalProtocolStoreImpl getSignalProtocolStore() {
        return signalProtocolStore;
    }

    public Optional<Contact> getContactByNumber(String number) {
        FilteredList<Contact> filtered = contacts.filtered(c -> number.equals(c.getNr()));
        System.err.println("resulting contact for nr "+number+" = "+filtered);
        if (filtered.size() == 0) System.err.println("CONTACTS = "+contacts);
        return Optional.ofNullable(filtered.size() > 0 ? filtered.get(0): null);
    }
   
    public static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Set the current credentialsProvider. This will also update the (persistent)
     * store, which is the ultimate source for this info.
     * @param credentialsProvider 
     */
    public void setCredentialsProvider(StaticCredentialsProvider credentialsProvider) {
        signalProtocolStore.setCredentialsProvider(credentialsProvider);
        this.credentialsProvider = signalProtocolStore.getCredentialsProvider();
    }

    class ClientConnectivityListener implements ConnectivityListener {

        @Override
        public void onConnected() {
            System.err.println("[CL] connected");
            Thread.dumpStack();
        }

        @Override
        public void onConnecting() {
            System.err.println("[CL] connecting");
        }

        @Override
        public void onDisconnected() {
            System.err.println("[CL] disconnected");
        }

        @Override
        public void onAuthenticationFailure() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean onGenericFailure(Response response, Throwable throwable) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
}
