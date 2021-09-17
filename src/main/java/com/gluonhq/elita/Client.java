package com.gluonhq.elita;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gluonhq.elita.crypto.KeyUtil;
// import com.gluonhq.elita.crypto.KeyUtil;
import com.gluonhq.elita.storage.User;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
//import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
//import org.whispersystems.signalservice.api.crypto.ContentHint;
//import org.whispersystems.signalservice.api.crypto.EnvelopeContent;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
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

//import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.*;
import signalservice.DeviceMessages.*;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class Client { // implements WebSocketInterface.Listener {
    static final String SIGNAL_USER_AGENT = "Signal-Desktop/5.14.0 Linux";

    static final String SERVER_NAME = "https://textsecure-service.whispersystems.org";
    static final String PREKEY_PATH = "/v2/keys/%s";
    private static final String MESSAGE_PATH = "/v1/messages/%s";
    private static final Map<String, String> NO_HEADERS = Collections.emptyMap();

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
    
    public Client(Elita elita) {
        this.lock = new LockImpl();
        this.elita = elita;
        this.webApi = new WebAPI(this, SERVER_NAME);
        this.webSocket = new WebSocketInterface();
        this.provisioningCipher = new ProvisioningCipher();
        this.sr = new SecureRandom();
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

    public void createAccount(ProvisionMessage pm, String deviceName) throws JsonProcessingException, IOException {
        System.err.println("Creating device " + deviceName);
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = Base64.getEncoder().encodeToString(password.getBytes());

        password = password.substring(0, password.length() - 2);
        regid = new SecureRandom().nextInt(16384) & 0x3fff;
        webApi.confirmCode(pm.getNumber(), pm.getProvisioningCode(), password,
                regid, deviceName, pm.getUuid());
        System.err.println("got code");
        UUID uuid = UUID.fromString(pm.getUuid());
        this.credentialsProvider = new StaticCredentialsProvider(uuid,
                pm.getNumber(), password, "signalingkey");
        this.signalServiceAddress = new SignalServiceAddress(uuid, pm.getNumber());

        generateAndRegisterKeys();
        store.setRegistrationId(regid);
      //  store = new SignalProtocolStoreImpl(identityKeypair, regid);
      //  Elita.setStore(store);
 //       store = new InMemorySignalProtocolStore(identityKeypair, regid);
        finishRegistration();
    }

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

    private void connect(boolean firstrun) throws IOException, InvalidKeyException, org.whispersystems.libsignal.UntrustedIdentityException {
        if (connecting) {
            Thread.dumpStack();
            throw new RuntimeException("Should not connect while connecting!");
        }
        connecting = true;
        this.webApi.getConfig(msg -> this.remoteConfig = msg);

        synchronizeData();
        webApi.registerSupportForUnauthenticatedDelivery();
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
        webApi.registerCapabilities();
        try {
            sendRequestKeySyncMessage();
            sendRequestGroupSyncMessage();
            sendRequestContactSyncMessage();
//      await this.confirmKeys(keys);
//      await this.registrationDone();
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
        SignalServiceMessageSender sender = 
                new SignalServiceMessageSender(credentialsProvider, store, lock);
      //  sender.sendMessage(message, org.whispersystems.libsignal.util.guava.Optional.absent());

        OutgoingPushMessageList messages = sender.createMessageBundle(message, org.whispersystems.libsignal.util.guava.Optional.absent());
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
        SignalServiceMessageSender sender = new SignalServiceMessageSender(credentialsProvider, store, lock);

        OutgoingPushMessageList messages = sender.createMessageBundle(message, org.whispersystems.libsignal.util.guava.Optional.absent());
        String destination = messages.getDestination();
        System.err.println("CONTACTdest = " + destination);
        webApi.fetch(String.format(MESSAGE_PATH, messages.getDestination()), "PUT", JsonUtil.toJson(messages));
    }

    private void sendRequestGroupSyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {
        String myUuid = webApi.getMyUuid();
        String myNumber = webApi.getMyNumber();
        System.err.println("SYNC GROUPS");
        Request request = Request.newBuilder().setType(Request.Type.GROUPS).build();

        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
        SignalServiceMessageSender sender = new SignalServiceMessageSender(credentialsProvider, store, lock);

        OutgoingPushMessageList messages = sender.createMessageBundle(message, org.whispersystems.libsignal.util.guava.Optional.absent());
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

        ObjectMapper mapper = new ObjectMapper();

        String jsonData = mapper.writeValueAsString(new PreKeyState(entities, signedPreKeyEntity, identityKey));
        // NOT USING SocketManager, hence http
        this.webApi.fetchHttp("PUT", String.format(PREKEY_PATH, ""), jsonData);
    }

    private static SignalServiceProtos.SyncMessage.Builder createSyncMessageBuilder() {
        SecureRandom random = new SecureRandom();
        byte[] padding = Util.getRandomLengthBytes(512);
        random.nextBytes(padding);

        SignalServiceProtos.SyncMessage.Builder builder = SignalServiceProtos.SyncMessage.newBuilder();
        builder.setPadding(ByteString.copyFrom(padding));

        return builder;
    }

    private void InMemorySignalProtocolStore(IdentityKeyPair identityKeypair, int regid) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
/*
    private void differentsendRequestKeySyncMessage() throws IOException, UntrustedIdentityException, InvalidKeyException {
        String myUuid = webApi.getMyUuid();
        String myNumber = webApi.getMyNumber();

        Request request = Request.newBuilder().setType(Request.Type.KEYS).build();
        RequestMessage requestMessage = new RequestMessage(request);
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(requestMessage);
       // SignalServiceMessageSender sender = new SignalServiceMessageSender(credentialsProvider, store);
        TrustStore trustStore = new TrustStoreImpl();
        SignalServiceUrl ssu = new SignalServiceUrl("", trustStore);
        SignalServiceConfiguration ssc = new SignalServiceConfiguration(
                new SignalServiceUrl[]{new SignalServiceUrl("https://textsecure-service.whispersystems.org", trustStore)},
                new SignalCdnUrl[]{new SignalCdnUrl("https://cdn.signal.org", trustStore),
                    new SignalCdnUrl("https://cdn2.signal.org", trustStore)},
                new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl("https://api.directory.signal.org", trustStore)} );
        Optional<SignalServiceMessagePipe> ssmp = Optional.absent();
        Optional<SignalServiceMessagePipe> ssmp2 = Optional.absent();
        Optional<EventListener> ssmp3 = Optional.absent();
        org.whispersystems.libsignal.util.guava.Optional<Object> nop = org.whispersystems.libsignal.util.guava.Optional.absent();
        SignalServiceMessageSender sender = new SignalServiceMessageSender(ssc, credentialsProvider, store, SIGNAL_USER_AGENT, true,
        ssmp,ssmp2,ssmp3);
        sender.sendMessage(message, org.whispersystems.libsignal.util.guava.Optional.absent());
   }
*/
    private static Map<Integer, SignalCdnUrl[]> makeSignalCdnUrlMapFor(SignalCdnUrl[] cdn0Urls, SignalCdnUrl[] cdn2Urls) {
        Map<Integer, SignalCdnUrl[]> result = new HashMap<>();
        result.put(0, cdn0Urls);
        result.put(2, cdn2Urls);
        return Collections.unmodifiableMap(result);
    }

    void processSyncMessage(SignalServiceSyncMessage sssm) throws InvalidMessageException {
        if (sssm.getContacts().isPresent()) {
            ContactsMessage msg = sssm.getContacts().get();
            SignalServiceAttachment att = msg.getContactsStream();
            SignalServiceAttachmentPointer pointer = att.asPointer();
            int cdnNumber = pointer.getCdnNumber();
            String path = new String(pointer.getRemoteId().getV3().get());
            ContentResponse response = webApi.fetchCdnHttp("GET", "/attachments/"+ path, null);
            byte[] bytes = response.getContent();
            File output = new File("/tmp/"+ path);
            try {
                Files.write(output.toPath(), bytes);
                InputStream is = AttachmentCipherInputStream.createForAttachment(output.getAbsoluteFile(), pointer.getSize().or(0), pointer.getKey(), pointer.getDigest().get());
                Files.copy(is, new File("/tmp/myin").toPath(), StandardCopyOption.REPLACE_EXISTING);
                readContacts();
            } catch (IOException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static void readContacts() throws FileNotFoundException, IOException {
        File f = new File("/tmp/myin");
        InputStream ois = new FileInputStream(f);
        DeviceContactsInputStream is = new DeviceContactsInputStream(ois);
        DeviceContact dc = is.read();
        while (dc != null) {
            System.err.println("Got contact: "+dc.getName());
            if (dc.getAvatar().isPresent()) {
                SignalServiceAttachmentStream ssas = dc.getAvatar().get();
                long length = ssas.getLength();
                InputStream inputStream = ssas.getInputStream();
                byte[] b = new byte[(int)length];
                inputStream.read(b);
                String nr = dc.getAddress().getNumber().get();
                File img = new File("/tmp/"+nr);
                com.google.common.io.Files.write(b, img);
            }
            System.err.println("Available? " + ois.available());
            if (ois.available() == 0) {
                dc = null;
            } else {
                dc = is.read();
            }
        }
    }

}
