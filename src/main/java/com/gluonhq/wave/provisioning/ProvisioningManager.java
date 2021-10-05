package com.gluonhq.wave.provisioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gluonhq.elita.Client;
import com.gluonhq.elita.Elita;
import com.gluonhq.elita.ProvisioningCipher;
import com.gluonhq.elita.TrustStoreImpl;
import com.gluonhq.elita.crypto.KeyUtil;
import com.gluonhq.wave.WaveManager;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import okhttp3.Response;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
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

    private WebSocketConnection provisioningWebSocket;
    private boolean listen = false;

    private int deviceId = -1;
    private StaticCredentialsProvider credentialsProvider;

    String dest = "wss://textsecure-service.whispersystems.org";
    private PushServiceSocket accountSocket;
    private String number = null;
    private ProvisionMessage pm = null;
    
    private ProvisioningClient elita;
    private WaveManager waveManager;

    /**
     * Flow: when the start method is invoked, this class will generate a URL
     * A callback is invoked (setProvivisioningUrl), allowing the caller to display the URL as a QR code.
     * When the QR code is scanned by a Signal-registered device, we will get called.
     * We then invoke the gotProvisioningNumber containing the number that scanned 
     * the URL. The caller can then invoke the createAccount(number) method to finalize the
     * registration.
     * @param elita 
     */
    public ProvisioningManager(WaveManager wave, ProvisioningClient elita) {
        this.elita = elita;
        this.waveManager = wave;
        this.trustStore = new TrustStoreImpl();
        this.provisioningCipher = new ProvisioningCipher(waveManager);
        StdErrLog logger = new StdErrLog();
        logger.setLevel(StdErrLog.LEVEL_INFO);        
        Log.setLog(logger);
    }

    public void start() {
        ConnectivityListener connectivityListener = new ProvisioningConnectivityListener("prov");
        SleepTimer sleepTimer = m -> Thread.sleep(m);
        provisioningWebSocket = new WebSocketConnection(dest, "provisioning/", trustStore,
                Optional.empty(), USER_AGENT, connectivityListener, sleepTimer,
                new LinkedList(), Optional.empty(), Optional.empty());
        provisioningWebSocket.connect();
        this.listen = true;
        try {
            while (listen) {
                System.err.println("waiting for reqest... ");
                WebSocketRequestMessage request = provisioningWebSocket.readRequest(60000);
                System.err.println("got readrequest: " + request);
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
        provisioningWebSocket.disconnect();
        System.err.println("[PM] stopped");
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
            elita.gotProvisioningUrl(url);
        } else if ("/v1/message".equals(path)) {
            try {
                DeviceMessages.ProvisionEnvelope envelope = DeviceMessages.ProvisionEnvelope.parseFrom(data);
                ProvisionMessage pm = provisioningCipher.decrypt(envelope);
                this.pm = pm;
                this.number = pm.getNumber();
                elita.gotProvisionMessage(pm.getNumber());
                this.stop();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            System.err.println("[PM] UNKNOWNPROVISIONINGMESSAGE");
            throw new IllegalArgumentException("UnknownProvisioningMessage");
        }
    }

    private String createPassword() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = Base64.getEncoder().encodeToString(password.getBytes());
        password = password.substring(0, password.length() - 2);
        return password;
    }

    private void startPreAccountWebSocket() {
        SignalServiceConfiguration config = Client.createConfiguration();
        CredentialsProvider emptyCredentials = new StaticCredentialsProvider(null, "", "");
        this.accountSocket = new PushServiceSocket(config, emptyCredentials, 
                Client.SIGNAL_USER_AGENT, null, true);
    }

    private void startAccountWebSocket() {
        this.accountSocket.cancelInFlightRequests();
        SignalServiceConfiguration config = Client.createConfiguration();
        this.accountSocket = new PushServiceSocket(config, credentialsProvider, 
                Client.SIGNAL_USER_AGENT, null, true);
    }
    
    public void createAccount(String nr, String deviceName) throws JsonProcessingException, IOException {
        System.err.println("Creating device " + deviceName+" for number "+this.number);
        if (!nr.equals(this.number)) {
            throw new IllegalArgumentException("Can't create account for " + nr);
        }
        startPreAccountWebSocket();
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = Base64.getEncoder().encodeToString(password.getBytes());
        password = password.substring(0, password.length() - 2);
        int regid = new SecureRandom().nextInt(16384) & 0x3fff;
        confirmCode(pm.getNumber(), pm.getProvisioningCode(), password,
                regid, deviceName, pm.getUuid());
        UUID uuid = UUID.fromString(pm.getUuid());
        this.credentialsProvider = new StaticCredentialsProvider(uuid,
                pm.getNumber(), password, "signalingkey", deviceId);
        startAccountWebSocket();
        waveManager.storeCredentialsProvider(this.credentialsProvider);
        generateAndRegisterKeys();
    }

    public void confirmCode(String number, String code, String pwd,
            int registrationId, String deviceName, String uuid) throws JsonProcessingException {
        System.err.println("Confirm code");
        String body = getDeviceMapData(deviceName, registrationId);
        System.err.println("body = " + body);
        String username = number;
        String authbase = username + ":" + pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        System.err.println("result of " + authbase + " conv = " + basicAuth);
        try {
            Map<String, String> myHeaders = new HashMap<>();
            myHeaders.put("Authorization", "Basic " + basicAuth);
            myHeaders.put("content-type", "application/json;charset=utf-8");
            myHeaders.put("User-Agent", "Signal-Desktop/5.14.0 Linux");
            myHeaders.put("x-signal-agent", "OWD");
            String response = accountSocket.makeServiceRequest("/v1/devices/" + code, "PUT", body, myHeaders);
            System.err.println("GOT RESPONSE: " + response);
            int c = response.indexOf(":");
            String did = response.substring(c + 1, response.length() - 1);
            this.deviceId = Integer.parseInt(did);
            System.err.println("did = " + deviceId);
            Elita.getSignalProtocolStore().setRegistrationId(deviceId);
        } catch (Exception e) {
            System.err.println("confirmcode Got error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ObjectNode createDefaultCapabilities() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.put("announcementGroup", true);
        capabilities.put("gv2-3", true);
        capabilities.put("gv1-migration", true);
        capabilities.put("senderKey", true);
        return capabilities;
    }

    private String getDeviceMapData(String name, int registrationId) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode capabilities = createDefaultCapabilities();
        ObjectNode jsonData = mapper.createObjectNode();
        System.err.println("DOOH");
        jsonData.set("capabilities", capabilities);
        jsonData.put("fetchesMessages", true);
        jsonData.put("name", name);
        jsonData.put("registrationId", registrationId);
        jsonData.put("supportsSms", false);
        jsonData.put("unrestrictedUnidentifiedAccess", false);
        String answer = mapper.writeValueAsString(jsonData);
        return answer;
    }

    public void generateAndRegisterKeys() throws IOException {
        IdentityKeyPair identityKeypair = waveManager.getSignalProtocolStore().getIdentityKeyPair();
        SignedPreKeyRecord signedPreKey = KeyUtil.generateSignedPreKey(identityKeypair, true);
        List<PreKeyRecord> records = KeyUtil.generatePreKeys(100);
        System.err.println("GARK, ik = "+ identityKeypair+" with pubkey = "+identityKeypair.getPublicKey()+" and spk = "+signedPreKey+" and records = "+records);
        String response = accountSocket.registerPreKeys(identityKeypair.getPublicKey(), signedPreKey, records);
        System.err.println("Response for generateAndRegisterKeys = "+response);
    }

    class ProvisioningConnectivityListener implements ConnectivityListener {

        private final String name;

        ProvisioningConnectivityListener(String name) {
            this.name = name;
        }

        @Override
        public void onConnected() {
            System.err.println("[PM] " + name + " connected");
        }

        @Override
        public void onConnecting() {
            System.err.println("[PM] " + name + " connecting");
        }

        @Override
        public void onDisconnected() {
            System.err.println("[PM] " + name + " disconnected");
        }

        @Override
        public void onAuthenticationFailure() {
            throw new UnsupportedOperationException("[PM] " + name + " Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean onGenericFailure(Response response, Throwable throwable) {
            throw new UnsupportedOperationException("[PM] " + name + " Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
}
