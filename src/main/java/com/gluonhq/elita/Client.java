package com.gluonhq.elita;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gluonhq.elita.crypto.KeyUtil;
import com.gluonhq.elita.model.Account;
import com.gluonhq.elita.storage.User;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.whispersystems.websocket.messages.WebSocketRequestMessage;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.internal.push.PreKeyEntity;
import org.whispersystems.signalservice.internal.push.PreKeyState;

//import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.*;
import signalservice.DeviceMessages.*;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class Client implements WebSocketInterface.Listener {

    static final String SERVER_NAME = "https://textsecure-service.whispersystems.org";
static final String PREKEY_PATH = "/v2/keys/%s";
    final WebSocketInterface webSocket;
    private final ProvisioningCipher provisioningCipher;
    private final SecureRandom sr;
    SocketManager socketManager;
    final WebAPI webApi;

    private final Elita elita;
    HttpClient httpClient;

    public Client(Elita elita) {
        this.elita = elita;
        this.webApi = new WebAPI(this, SERVER_NAME);
        this.webSocket = new WebSocketInterface();
        this.provisioningCipher = new ProvisioningCipher();
        this.sr = new SecureRandom();
        //  this.socketManager = new SocketManager(this, null, null, null, null);
    }

    public void startup() {
        this.socketManager = this.webApi.connect(User.getUserName(), User.getPassword());
        this.webApi.getConfig();
        this.webApi.provision();
    }

    public void createAccount(ProvisionMessage pm, String deviceName) throws JsonProcessingException, IOException {
        System.err.println("Creating device " + deviceName);
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = password.substring(0, password.length() - 2);
        int regid = new SecureRandom().nextInt(16384) & 0x3fff;
        webApi.confirmCode(pm.getNumber(), pm.getProvisioningCode(), password, regid, deviceName, pm.getUuid());
        System.err.println("got code");
        Account account = new Account(pm.getNumber(), pm.getProvisioningCode());
        generateAndRegisterKeys();
        
//      await clearSessionsAndPreKeys();
//      const keys = await generateKeys();
//      await this.server.registerKeys(keys);
//      await this.confirmKeys(keys);
//      await this.registrationDone();
    }

    public void provisioningMessageReceived(WebSocketRequestMessage requestMessage) {
        String path = requestMessage.getPath();
        System.out.println("[JVDBG] GOT request from path " + path);
        Optional<byte[]> body = requestMessage.getBody();
        byte[] data = body.get();
        if ("/v1/address".equals(path)) {
            String uuid = "";
            try {
                ProvisioningUuid puuid = ProvisioningUuid.parseFrom(data);
                uuid = puuid.getUuid();
            } catch (InvalidProtocolBufferException ex) {
                ex.printStackTrace();
            }
            System.err.println("MSG = " + uuid);
            String ourPubKey = Base64.getEncoder().encodeToString(this.provisioningCipher.ourKeyPair.getPublicKey().serialize());
            ourPubKey = URLEncoder.encode(ourPubKey, StandardCharsets.UTF_8);
            String url = "tsdevice:/?uuid=" + uuid + "&pub_key=" + ourPubKey;
            System.err.println("URL = " + url);
            elita.setProvisioningURL(url);
        } else if ("/v1/message".equals(path)) {
            try {
                ProvisionEnvelope envelope = ProvisionEnvelope.parseFrom(data);
                ByteString publicKey = envelope.getPublicKey();
                ProvisionMessage pm = provisioningCipher.decrypt(envelope);
                System.err.println("Got pm: " + pm);
                elita.gotProvisionMessage(pm);
                //  const deviceName = await confirmNumber(provisionMessage.number);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onReceivedRequest(WebSocketRequestMessage requestMessage) {
        provisioningMessageReceived(requestMessage);

        try {
            webSocket.sendResponse(requestMessage.getRequestId(), 200, "OK", "world!".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReceivedResponse(WebSocketResponseMessage responseMessage) {
        System.err.println("[JVDBG] Got response: " + responseMessage.getStatus());

        if (responseMessage.getBody().isPresent()) {
            System.err.println("[JVDBG] Got response body: " + new String(responseMessage.getBody().get()));
        }
    }

    @Override
    public void onClosed() {
        System.err.println("[Client] WebSocket onClosed() called");
    }

    @Override
    public void onConnected() {
        try {
            System.err.println("[Client] WebSocket onConnected called");
            Thread.dumpStack();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void attached(WebSocketInterface parent) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void generateAndRegisterKeys() throws IOException {
        IdentityKeyPair identityKey = KeyUtil.getIdentityKeyPair();
        SignedPreKeyRecord signedPreKey = KeyUtil.generateSignedPreKey(identityKey, true);
        List<PreKeyRecord> records = KeyUtil.generatePreKeys(100);
        registerPreKeys(identityKey.getPublicKey(), signedPreKey, records);
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
        this.webApi.fetchHttp("PUT", String.format(PREKEY_PATH, ""),jsonData);
    }


}
