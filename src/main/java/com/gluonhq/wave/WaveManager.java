package com.gluonhq.wave;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gluonhq.elita.LockImpl;
import com.gluonhq.elita.SignalProtocolStoreImpl;
// import static com.gluonhq.elita.SocketManager.getCertificateValidator;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.eclipse.jetty.client.api.ContentResponse;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;
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

    static final TrustStore trustStore = new TrustStoreImpl();
    final SignalServiceConfiguration signalServiceConfiguration;

    private final SignalProtocolStoreImpl signalProtocolStore;

    private CredentialsProvider credentialsProvider;
    private LockImpl lock;
    private SignalServiceAddress signalServiceAddress;
    private SignalServiceMessageReceiver receiver;
    private SignalServiceMessageSender sender;
    private boolean connected;
    
    private final static String SIGNAL_FX;

    static {
        SIGNAL_FX = System.getProperty("user.home")
                + File.separator + ".signalfx";
        File dir = new File(SIGNAL_FX);
        dir.mkdirs();
    }
    private MessagingClient messageListener;
    public WaveManager() {
        signalServiceConfiguration = createConfiguration();
        signalProtocolStore = new SignalProtocolStoreImpl();
        lock = new LockImpl();
        try {
            restoreCredentialsProvider();
            retrieveIdentityKeyPair();
        } catch (IOException e) {
            System.err.println("[WaveManager] no credentials found!");
        } catch (InvalidKeyException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void initalize() {
        if (!isInitialized()) {
        }
    }

    public boolean isInitialized() {
        return Files.exists(getCredentialsPath());
    }

    public void startProvisioning(ProvisioningClient provisioningClient) {
        provisioningManager = new ProvisioningManager(this, provisioningClient);
        provisioningManager.start();
    }

    public void createAccount(String nr, String deviceName) throws JsonProcessingException, IOException {
        provisioningManager.createAccount(nr, deviceName);
        provisioningManager.stop();
    }
    
    // MESSAGES
    public void setMessageListener(MessagingClient mc) {
        this.messageListener = mc;
    }

    // CONTACTS
    private ObservableList<Contact> contacts = FXCollections.observableArrayList();
    private boolean contactStorageDirty = true;

    public ObservableList<Contact> getContacts() {
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
        File dir = new File(SIGNAL_FX);
        Path path = dir.toPath().resolve("contacts");
        List<String> lines = Files.readAllLines(path);
        List<Contact> answer = new LinkedList<>();
        for (int i = 0; i < lines.size(); i = i + 3) {
            Contact c = new Contact(lines.get(i), lines.get(i+1), lines.get(i+2));
            answer.add(c);
        }
        return answer;
    }
    
    private void storeContacts() throws IOException {
        File dir = new File(SIGNAL_FX);
        Path path = dir.toPath().resolve("contacts");
        List<String> lines = new LinkedList<String>();
        for (Contact contact : contacts) {
            lines.add(contact.getName());
            lines.add(contact.getUuid());
            lines.add(contact.getNr());
        }
        Files.write(path, lines);
        contactStorageDirty = true;
    }

    public void syncContacts() throws IOException, UntrustedIdentityException, InvalidKeyException {
        ensureConnected();
        System.err.println("SYNC CONTACTS");
        SignalServiceProtos.SyncMessage.Request request = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        sender.sendMessage(message, Optional.absent());
    }
    
    public void sendMessage(String uuid, String text) {
        ensureConnected();
        Contact target = contacts.stream().filter(c -> uuid.equals(c.getUuid())).findFirst().get();

        Optional<SignalServiceAddress> add = SignalServiceAddress.fromRaw(uuid, target.getNr());
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder().withBody(text).build();
        try {
            sender.sendMessage(add.get(), Optional.absent(), message);
        } catch (UntrustedIdentityException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WaveManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    static Path getCredentialsPath() {
//        String dirname = System.getProperty("user.home")
//                + File.separator + ".signalfx";
        File dir = new File(SIGNAL_FX);
//        dir.mkdirs();
        Path path = dir.toPath().resolve("credentials");
        return path;
    }

    /**
     * Save the byes for this keypair to persistent storage and update the
     * instance store
     *
     * @param b
     * @throws InvalidKeyException
     */
    public void saveIdentityKeyPair(byte[] b) throws InvalidKeyException {
        String dirname = System.getProperty("user.home")
                + File.separator + ".signalfx";
        File dir = new File(dirname);
        dir.mkdirs();
        Path path = dir.toPath().resolve("keypair");
        try {
            Files.write(path, b, StandardOpenOption.CREATE);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        storeIdentityKeyPair(b);
    }

    void storeIdentityKeyPair(byte[] b) throws InvalidKeyException {
        ECPrivateKey privateKey = Curve.decodePrivatePoint(b);
        ECPublicKey publicKey = Curve.createPublicKeyFromPrivateKey(b);
        ECKeyPair keyPair = new ECKeyPair(publicKey, privateKey);
        System.err.println("identitykp = " + keyPair);
        IdentityKey identityKey = new IdentityKey(publicKey);
        IdentityKeyPair ikp = new IdentityKeyPair(identityKey, privateKey);
        signalProtocolStore.setIdentityKeyPair(ikp);
    }

    void retrieveIdentityKeyPair() throws InvalidKeyException, IOException {
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
        SignalServiceMessagePipe messagePipe = receiver.createMessagePipe();
        SignalServiceMessagePipe unidentifiedMessagePipe = receiver.createUnidentifiedMessagePipe();
        processMessagePipe(messagePipe);
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
                Optional.absent(),
                null,
                executorService,
                512 * 1024,
                true);
        return sender;
    }

    void processMessagePipe(SignalServiceMessagePipe pipe) {
        Thread t = new Thread() {
            @Override
            public void run() {
                boolean listen = true;
                while (listen) {
                    try {
                        System.err.println("[PIPE] waiting for envelope...");
                        SignalServiceEnvelope envelope = pipe.read(20, TimeUnit.SECONDS);
                        System.err.println("[PIPE] got envelope");
                        SignalServiceContent content = mydecrypt(envelope);
                        System.err.println("[PIPE] got content: " + content);
                        System.err.println("[PIPE] envelope Type = " + envelope.getType());
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

    SignalServiceContent mydecrypt(SignalServiceEnvelope sse) throws Exception {
        SignalServiceCipher cipher = new SignalServiceCipher(signalServiceAddress,
                signalProtocolStore,
                new LockImpl(),
                getCertificateValidator());
        SignalServiceContent content = cipher.decrypt(sse);
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
            this.messageListener.gotMessage(uuid, content);
        }
        
    }
    
    private void processContactsMessage(ContactsMessage msg) throws IOException {
        SignalServiceAttachment att = msg.getContactsStream();
        SignalServiceAttachmentPointer pointer = att.asPointer();
        int cdnNumber = pointer.getCdnNumber();
        String path = new String(pointer.getRemoteId().getV3().get());
        ContentResponse response = Networking.httpRequest("https://cdn2.signal.org", "GET", "/attachments/" + path, null, null);
        Path output = Files.createTempFile("pre", "post");
        Files.write(output, response.getContent());
        try {
            InputStream ais = AttachmentCipherInputStream.createForAttachment(output.toFile(), pointer.getSize().or(0), pointer.getKey(), pointer.getDigest().get());
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
                if (dc.getAvatar().isPresent()) {
                    SignalServiceAttachmentStream ssas = dc.getAvatar().get();
                    long length = ssas.getLength();
                    InputStream inputStream = ssas.getInputStream();
                    byte[] b = new byte[(int) length];
                    inputStream.read(b);
                    String nr = dc.getAddress().getNumber().get();
                    File img = new File("/tmp/" + nr);
                    com.google.common.io.Files.write(b, img);
                }
                Contact contact = new Contact(dc.getName().or("anonymous"),
                        dc.getAddress().getUuid().get().toString(),
                        dc.getAddress().getNumber().or("123"));
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
                Optional.absent(), Optional.absent(), null
        );
        return answer;
    }

    // get credentials info from storage and populate instance fields.
    private final void restoreCredentialsProvider() throws IOException {
        Path path = getCredentialsPath();
        List<String> lines = Files.readAllLines(path);
        String uuidString = lines.get(0);
        UUID uuid = UUID.fromString(uuidString);
        String number = lines.get(1);
        String password = lines.get(2);
        int deviceId = Integer.parseInt(lines.get(3));
        this.signalProtocolStore.setDeviceId(deviceId);
        this.signalProtocolStore.setMyUuid(uuidString);

        this.credentialsProvider = new StaticCredentialsProvider(uuid,
                number, password, "signalingkey", deviceId);
        this.signalServiceAddress = new SignalServiceAddress(uuid, number);

    }

    void storeCredentialsProvider() throws IOException {
        storeCredentialsProvider(this.credentialsProvider);
    }

    public static void storeCredentialsProvider(CredentialsProvider cp) throws IOException {
        if (cp == null) {
            throw new IllegalArgumentException("No CredentialsProvider");
        }
        Path path = getCredentialsPath();
        File credFile = path.toFile();
        if (credFile.exists()) {
            credFile.delete();
        }

        UUID uuid = cp.getUuid();

        Files.writeString(path, uuid.toString() + "\n", StandardOpenOption.CREATE);
        Files.writeString(path, cp.getE164() + "\n", StandardOpenOption.APPEND);
        Files.writeString(path, cp.getPassword() + "\n", StandardOpenOption.APPEND);
        Files.writeString(path, Integer.toString(cp.getDeviceId()) + "\n", StandardOpenOption.APPEND);
        Files.writeString(path, cp.getSignalingKey() + "\n", StandardOpenOption.APPEND);
    }

    public SignalProtocolStoreImpl getSignalProtocolStore() {
        return signalProtocolStore;
    }

    public static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
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
