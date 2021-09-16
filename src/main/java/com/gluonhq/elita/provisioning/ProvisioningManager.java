/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita.provisioning;

import com.gluonhq.elita.Elita;
import com.gluonhq.elita.ProvisioningCipher;
import com.gluonhq.elita.TrustStoreImpl;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import signalservice.DeviceMessages;
import signalservice.DeviceMessages.ProvisionMessage;

/**
 *
 * @author johan
 */
public class ProvisioningManager {

    private final TrustStore trustStore;
    private final ProvisioningCipher provisioningCipher;
    private final String USER_AGENT = "OWA";
    private final Elita elita;
    
    private WebSocketConnection webSocket;
    private ProvisionMessage provisionMessage;
    private boolean listen = false;
    
    public ProvisioningManager(Elita elita) {
        this.elita = elita;
        this.trustStore = new TrustStoreImpl();
        this.provisioningCipher = new ProvisioningCipher();
    }

    public void start() {
        String dest = "wss://textsecure-service.whispersystems.org";
        ConnectivityListener connectivityListener = new ProvisioningConnectivityListener();
        SleepTimer sleepTimer = m -> Thread.sleep(m);
        webSocket = new WebSocketConnection(dest, "provisioning/", trustStore,
                Optional.absent(), USER_AGENT, connectivityListener, sleepTimer);
        webSocket.connect();
        this.listen = true;
        try {
            while (listen) {
                WebSocketProtos.WebSocketRequestMessage request = webSocket.readRequest(60000);
                System.err.println("got readrequest: "+request);
                handleRequest(request);
            }
        } catch (Exception ex) {
            System.err.println("[PM] Exception while waiting for incoming request");
            this.listen = false;
            ex.printStackTrace();
        }
    }
    
    public void stop() {
        this.listen = false;
        System.err.println("[PM] we're asked to disconnect the websocket");
        webSocket.disconnect();
        System.err.println("[PM] tried to disconnect the websocket");
    }
    
    private void handleRequest(WebSocketProtos.WebSocketRequestMessage request) {
        String path = request.getPath();
        System.err.println("[PM] we need to handle a request for path " + path);
        ByteString data = request.getBody();
        if ("/v1/address".equals(path)) {
            String uuid = "";
            try {
                DeviceMessages.ProvisioningUuid puuid = DeviceMessages.ProvisioningUuid.parseFrom(data);
                uuid = puuid.getUuid();
            } catch (InvalidProtocolBufferException ex) {
                ex.printStackTrace();
            }
            System.err.println("[PM] we got a uuid: " + uuid);
            String ourPubKey = Base64.getEncoder().encodeToString(this.provisioningCipher.getOurKeyPair().getPublicKey().serialize());
            ourPubKey = URLEncoder.encode(ourPubKey, StandardCharsets.UTF_8);
            String url = "tsdevice:/?uuid=" + uuid + "&pub_key=" + ourPubKey;
            System.err.println("URL = " + url);
            elita.setProvisioningURL(url);
        } else if ("/v1/message".equals(path)) {
            try {
                DeviceMessages.ProvisionEnvelope envelope = DeviceMessages.ProvisionEnvelope.parseFrom(data);
                ByteString publicKey = envelope.getPublicKey();
                ProvisionMessage pm = provisioningCipher.decrypt(envelope);
                elita.gotProvisionMessage(pm);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            System.err.println("[PM] UNKNOWNPROVISIONINGMESSAGE");
            throw new IllegalArgumentException("UnknownProvisioningMessage");
        }
    }

    public void createAccount() {
        webSocket.disconnect();
        String pwd = createPassword();
        int regId = new SecureRandom().nextInt(16384) & 0x3fff;
    }
    
    private String createPassword () {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = Base64.getEncoder().encodeToString(password.getBytes());
        password = password.substring(0, password.length() - 2);
        return password;
    }


    class ProvisioningConnectivityListener implements ConnectivityListener {

        @Override
        public void onConnected() {
            System.err.println("[PM] connected");
        }

        @Override
        public void onConnecting() {
            System.err.println("[PM] connecting");
        }

        @Override
        public void onDisconnected() {
            System.err.println("[PM] disconnected");
        }

        @Override
        public void onAuthenticationFailure() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
    }
}
