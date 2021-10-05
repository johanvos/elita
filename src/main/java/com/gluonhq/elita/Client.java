package com.gluonhq.elita;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.gluonhq.elita.SocketManager.getCertificateValidator;
import com.gluonhq.elita.crypto.KeyUtil;
import com.gluonhq.elita.storage.User;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalServiceMessageSender.EventListener;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
//import org.whispersystems.signalservice.api.crypto.ContentHint;
//import org.whispersystems.signalservice.api.crypto.EnvelopeContent;
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
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
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
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.PreKeyEntity;
import org.whispersystems.signalservice.internal.push.PreKeyResponse;
import org.whispersystems.signalservice.internal.push.PreKeyResponseItem;
import org.whispersystems.signalservice.internal.push.PreKeyState;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse.Config;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Request;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;

import java.util.Optional;

//import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.*;
import signalservice.DeviceMessages.*;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class Client { // implements WebSocketInterface.Listener {

    public static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";
static final String SIGNAL_KEY_BACKUP_URL = "https://api.backup.signal.org";
static final String SIGNAL_STORAGE_URL = "https://storage.signal.org";
static final String SIGNAL_SERVICE_URL = "https://textsecure-service.whispersystems.org";
    static final String SERVER_NAME = "https://textsecure-service.whispersystems.org";
    static final String PREKEY_PATH = "/v2/keys/%s";
    private static final String MESSAGE_PATH = "/v1/messages/%s";
    private static final Map<String, String> NO_HEADERS = Collections.emptyMap();
   static final TrustStore trustStore = new TrustStoreImpl();

   final SignalServiceConfiguration signalServiceConfiguration;
   
    final WebSocketInterface webSocket;
    private final ProvisioningCipher provisioningCipher;
    private final SecureRandom sr;
    SocketManager socketManager;
    final WebAPI webApi;
    //   private final SignalServiceDataStoreImpl signalServiceDataStore = new SignalServiceDataStoreImpl();
    private CredentialsProvider credentialsProvider;

    private final Elita elita;
    HttpClient httpClient;
    private RemoteConfigResponse remoteConfig;
    private IdentityKeyPair identityKeypair;
    private int regid;
    final SignalProtocolStoreImpl store = Elita.getSignalProtocolStore();
    private SignalProtocolAddress me;
    private SignalServiceAddress signalServiceAddress;

    LockImpl lock;
    private SignalServiceMessageReceiver receiver;
    private SignalServiceMessageSender sender;

    public Client(Elita elita) {
        this.lock = new LockImpl();
        this.elita = elita;
        this.webApi = new WebAPI(this, SERVER_NAME);
        this.webSocket = new WebSocketInterface();
        this.provisioningCipher = new ProvisioningCipher(null);
        this.sr = new SecureRandom();
        this.signalServiceConfiguration = createConfiguration();
    }

    // we return the impl here, since we need the method to store device-identifier
    public SignalProtocolStore getSignalServiceDataStore() {
        return store;
    }

    public SignalServiceAddress getSignalServiceAddress() {
        return this.signalServiceAddress;
    }

    public SignalProtocolAddress getSignalLocalAddress() {
        return me;
    }

    public void startup() {
        this.socketManager = this.webApi.connect(User.getUserName(), User.getPassword());
        this.webApi.getConfig();
        this.webApi.onOffline();
        this.webApi.onOnline();
        //    this.webApi.provision();
    }

//    public void createAccount(ProvisionMessage pm, String deviceName) throws JsonProcessingException, IOException {
//        System.err.println("Creating device " + deviceName);
//        byte[] b = new byte[16];
//        new SecureRandom().nextBytes(b);
//        String password = new String(b, StandardCharsets.UTF_8);
//        password = Base64.getEncoder().encodeToString(password.getBytes());
//
//        password = password.substring(0, password.length() - 2);
//        regid = new SecureRandom().nextInt(16384) & 0x3fff;
//        webApi.confirmCode(pm.getNumber(), pm.getProvisioningCode(), password,
//                regid, deviceName, pm.getUuid());
//        System.err.println("got code");
//        UUID uuid = UUID.fromString(pm.getUuid());
//        this.credentialsProvider = new StaticCredentialsProvider(uuid,
//                pm.getNumber(), password, "signalingkey", webApi.getDeviceId());
//        storeCredentialsProvider();
//        this.signalServiceAddress = new SignalServiceAddress(uuid, pm.getNumber());
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
////connect();
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException ex) {
//            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        generateAndRegisterKeys();
//        store.setRegistrationId(regid);
//
//        finishRegistration();
//    }

    private void finishRegistration() throws IOException {
        this.webApi.authenticate();
        try {
            connect(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IOException(ex);
        }
    }

    int connectCount = 0;
    boolean connecting = false;

    public void connect(boolean firstrun) throws IOException, InvalidKeyException, org.whispersystems.libsignal.UntrustedIdentityException {
        if (connecting) {
            throw new RuntimeException("Should not connect while connecting!");
        }
        connecting = true;
        this.webApi.getConfig(msg -> this.remoteConfig = msg);

        synchronizeData();
        PreKeyResponse response = webApi.getKeysForIdentifier();

        List<PreKeyBundle> bundles = new LinkedList<>();

        for (PreKeyResponseItem device : response.getDevices()) {
            ECPublicKey preKey = null;
            ECPublicKey signedPreKey = null;
            byte[] signedPreKeySignature = null;
            int preKeyId = -1;
            int signedPreKeyId = -1;

            if (device.getSignedPreKey() != null) {
                signedPreKey = device.getSignedPreKey().getPublicKey();
                signedPreKeyId = device.getSignedPreKey().getKeyId();
                signedPreKeySignature = device.getSignedPreKey().getSignature();
            }

            if (device.getPreKey() != null) {
                preKeyId = device.getPreKey().getKeyId();
                preKey = device.getPreKey().getPublicKey();
            }

            PreKeyBundle pkBundle = new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId,
                    preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                    response.getIdentityKey());
            me = new SignalProtocolAddress(webApi.getMyUuid(), device.getDeviceId());
            if (device.getRegistrationId() != this.regid) {
                SessionBuilder sb = new SessionBuilder(store, me);
                sb.process(pkBundle);
            }
        }
//        
//        for (PreKeyResponseItem item : devkeys) {
//            int devid = item.getDeviceId();
//            SignalProtocolAddress spa = new SignalProtocolAddress(webApi.getMyUuid(), devid);
//            SessionRecord sr = new SessionRecord();
//            SessionState ss = sr.getSessionState();
//            
//            item.getSignedPreKey().
//            store.storeSession(spa, sr);
//        }
try {
        webApi.registerCapabilities();
            System.err.println("WE USED TO CONNECT HERE");
            connect();
            differentsendRequestKeySyncMessage();
      //      sendRequestGroupSyncMessage();
            differentsendRequestContactSyncMessage();
        } catch (UntrustedIdentityException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void processNewConfiguration(RemoteConfigResponse conf) throws IOException, UntrustedIdentityException {
        System.err.println("This configList has " + conf.getConfig().size() + " configs.");
        System.err.println("CFG0 = " + conf.getConfig().get(0));
        System.err.println("C0name = " + conf.getConfig().get(0).getName());
        System.err.println("C0val = " + conf.getConfig().get(0).getValue());
        for (Config config : conf.getConfig()) {
            if ("desktop.storage".equals(config.getName())) {
                try {
                    sendRequestKeySyncMessage();
                } catch (InvalidKeyException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void sendRequestKeySyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {
        String myUuid = webApi.getMyUuid();
        String myNumber = webApi.getMyNumber();

        Request request = Request.newBuilder().setType(Request.Type.KEYS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        SignalServiceMessageSender sender
                = new SignalServiceMessageSender(credentialsProvider, store, lock);
        //  sender.sendMessage(message, org.whispersystems.libsignal.util.guava.Optional.absent());

        OutgoingPushMessageList messages = sender.createMessageBundle(message, Optional.empty());
        String destination = messages.getDestination();
        System.err.println("dest = " + destination);
        webApi.fetch(String.format(MESSAGE_PATH, messages.getDestination()), "PUT", JsonUtil.toJson(messages));
    }

    private void sendRequestContactSyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {
        String myUuid = webApi.getMyUuid();
        String myNumber = webApi.getMyNumber();
        System.err.println("SYNC CONTACTS");
        Request request = Request.newBuilder().setType(Request.Type.CONTACTS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
    //    SignalServiceMessageSender sender = new SignalServiceMessageSender(credentialsProvider, store, lock);
sender.sendMessage(message, Optional.empty());
//        OutgoingPushMessageList messages = sender.createMessageBundle(message, org.whispersystems.libsignal.util.guava.Optional.absent());
//        String destination = messages.getDestination();
//        System.err.println("CONTACTdest = " + destination);
//        webApi.fetch(String.format(MESSAGE_PATH, messages.getDestination()), "PUT", JsonUtil.toJson(messages));
    }

    private void sendRequestGroupSyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {
        String myUuid = webApi.getMyUuid();
        String myNumber = webApi.getMyNumber();
        System.err.println("SYNC GROUPS");
        Request request = Request.newBuilder().setType(Request.Type.GROUPS).build();

        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        SignalServiceMessageSender sender = new SignalServiceMessageSender(credentialsProvider, store, lock);

        OutgoingPushMessageList messages = sender.createMessageBundle(message, Optional.empty());
        String destination = messages.getDestination();
        System.err.println("GROUPDEST = " + destination);
        webApi.fetch(String.format(MESSAGE_PATH, messages.getDestination()), "PUT", JsonUtil.toJson(messages));
    }

    private void synchronizeData() throws IOException {
        long startDate = ChronoUnit.DAYS.between(Instant.EPOCH, Instant.now());
        long endDate = startDate + 7;
        webApi.getGroupCredentials(startDate, endDate);
    }

    public void generateAndRegisterKeys() throws IOException {
        this.identityKeypair = Elita.getSignalProtocolStore().getIdentityKeyPair();
        SignedPreKeyRecord signedPreKey = KeyUtil.generateSignedPreKey(identityKeypair, true);
        List<PreKeyRecord> records = KeyUtil.generatePreKeys(100);
        registerPreKeys(identityKeypair.getPublicKey(), signedPreKey, records);
    }

    public void registerPreKeys(IdentityKey identityKey,
            SignedPreKeyRecord signedPreKey,
            List<PreKeyRecord> records)
            throws IOException {

        List<PreKeyEntity> entities = new LinkedList<>();

        for (PreKeyRecord record : records) {
            PreKeyEntity entity = new PreKeyEntity(record.getId(),
                    record.getKeyPair().getPublicKey());

            entities.add(entity);
        }

        SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                signedPreKey.getKeyPair().getPublicKey(),
                signedPreKey.getSignature());
// sender.getSocket().registerPreKeys(identityKey, signedPreKey, records);

ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(new PreKeyState(entities, signedPreKeyEntity, identityKey));
        this.webApi.fetchHttp("PUT", String.format(PREKEY_PATH, ""), jsonData);
    }

    public static SignalServiceConfiguration createConfiguration() {
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
    
    private SignalServiceMessageReceiver createMessageReceiver() {
        // ensure configuration and provider are ok
        assert(signalServiceConfiguration != null);
        assert(credentialsProvider != null);
        if (signalServiceConfiguration == null) throw new IllegalArgumentException ("no signalserviceconfiguration");
        if (credentialsProvider == null) throw new IllegalArgumentException ("no credentialsProvider");
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
    
    void processMessagePipe(SignalServiceMessagePipe pipe) {
        Thread t = new Thread() {
            @Override public void run() {
                boolean listen = true;
                while (listen) {
                    try {
                        System.err.println("[PIPE] waiting for envelope...");
                        SignalServiceEnvelope envelope = pipe.read(20, TimeUnit.SECONDS);
                        System.err.println("[PIPE] got envelope");
                        SignalServiceContent content = mydecrypt(envelope);
                        System.err.println("[PIPE] got content: "+content);
                        System.err.println("[PIPE] envelope Type = " + envelope.getType());
                        if (content != null) {
                            if (content.getSyncMessage().isPresent()) {
                                System.err.println("[PIPE] envelope has syncmessage");
                                SignalServiceSyncMessage sssm = content.getSyncMessage().get();
                                processSyncMessage(sssm);
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
    
    private SignalServiceMessageSender createMessageSender(SignalServiceMessageReceiver receiver) {
        SignalServiceMessagePipe messagePipe = receiver.createMessagePipe();
        SignalServiceMessagePipe unidentifiedMessagePipe = receiver.createUnidentifiedMessagePipe();
        processMessagePipe(messagePipe);
        ExecutorService executorService = new ScheduledThreadPoolExecutor(5);
        SignalServiceMessageSender sender = new SignalServiceMessageSender(
                signalServiceConfiguration,
                credentialsProvider,
                store,
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
    
    private boolean connected = false;
    
    public void ensureConnected() {
        if (connected) return;
        connect();
    }

    public void connect() {
        System.err.println("[CLIENT] create receiver");
        this.receiver = createMessageReceiver();
        System.err.println("[CLIENT] created receiver, wait a bit");
        this.sender = createMessageSender(receiver);
        this.connected = true;
    }

    private void differentsendRequestKeySyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {
        Request request = Request.newBuilder().setType(Request.Type.KEYS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);      
        sender.sendMessage(message, Optional.empty());
    }
        private void differentsendRequestContactSyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {

            Request request = Request.newBuilder().setType(Request.Type.CONTACTS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
            sender.sendMessage(message, Optional.empty());

        }
    void processSyncMessage(SignalServiceSyncMessage sssm) throws InvalidMessageException {
        if (sssm.getContacts().isPresent()) {
            ContactsMessage msg = sssm.getContacts().get();
            SignalServiceAttachment att = msg.getContactsStream();
            SignalServiceAttachmentPointer pointer = att.asPointer();
            int cdnNumber = pointer.getCdnNumber();
            String path = new String(pointer.getRemoteId().getV3().get());
            ContentResponse response = webApi.fetchCdnHttp("GET", "/attachments/" + path, null);
            byte[] bytes = response.getContent();
            File output = new File("/tmp/" + path);
            try {
                Files.write(output.toPath(), bytes);
                InputStream is = AttachmentCipherInputStream.createForAttachment(output.getAbsoluteFile(), pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get());
                Files.copy(is, new File("/tmp/myin").toPath(), StandardCopyOption.REPLACE_EXISTING);
                readContacts();
                fakesend();
            } catch (IOException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeyException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void readContacts() throws FileNotFoundException, IOException {
        File f = new File("/tmp/myin");
        InputStream ois = new FileInputStream(f);
        DeviceContactsInputStream is = new DeviceContactsInputStream(ois);
        DeviceContact dc = is.read();
        while (dc != null) {
            System.err.println("Got contact: " + dc.getName()+", uuid = "+dc.getAddress().getUuid()
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
       //         store.storeSession(dc.getAddress(), null);
            }
            System.err.println("Available? " + ois.available());
            if (ois.available() == 0) {
                dc = null;
            } else {
                dc = is.read();
            }
        }
    }
    
    public void fakesend() throws IOException, InvalidKeyException {
        if (this.credentialsProvider == null) {
            restoreCredentialsProvider();
        }
        if (store.getIdentityKeyPair() == null) {
            elita.retrieveIdentityKeyPair();
        }
        ensureConnected();
        String erwin = "1ffa2360-2e8a-41b3-baa2-9c4c50f2c008";
        String k = "f85468c1-6e8c-44df-9e4e-43d676910a4b";
        String nr = "+32474996562";
        nr = "+32486497465";
        Optional<SignalServiceAddress> add = SignalServiceAddress.fromRaw(k, nr);
        SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder().withBody("Elita test").build();
        try {
            sender.sendMessage(add.get(), Optional.empty(), message);
        } catch (IOException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UntrustedIdentityException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    SignalServiceContent mydecrypt(SignalServiceEnvelope sse) throws Exception {
        SignalServiceCipher cipher = new SignalServiceCipher(getSignalServiceAddress(),
                getSignalServiceDataStore(),
                new LockImpl(),
                getCertificateValidator());
        SignalServiceContent content = cipher.decrypt(sse);
        return content;
    }

    static Path getCredentialsPath() {
        String dirname = System.getProperty("user.home")
                + File.separator + ".signalfx";
        File dir = new File(dirname);
        dir.mkdirs();
        Path path = dir.toPath().resolve("credentials");
        return path;
    }

    // get credentials info from storage and populate instance fields.
    void restoreCredentialsProvider() throws IOException {
        Path path = getCredentialsPath();
        List<String> lines = Files.readAllLines(path);
        String uuidString = lines.get(0);
        UUID uuid = UUID.fromString(uuidString);
        String number = lines.get(1);
        String password = lines.get(2);
        int deviceId = Integer.parseInt(lines.get(3));
        this.store.setDeviceId(deviceId);
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

        Files.writeString(path, uuid.toString()+"\n", StandardOpenOption.CREATE);
        Files.writeString(path, cp.getE164()+"\n", StandardOpenOption.APPEND);
        Files.writeString(path, cp.getPassword()+"\n", StandardOpenOption.APPEND);
        Files.writeString(path, Integer.toString(cp.getDeviceId())+"\n", StandardOpenOption.APPEND);
        Files.writeString(path, cp.getSignalingKey()+"\n", StandardOpenOption.APPEND);
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
