package com.gluonhq.elita;

import com.gluonhq.elita.model.Account;
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
import java.util.Optional;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

//import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.*;
import signalservice.DeviceMessages.*;

@WebSocket(maxTextMessageSize = 64 * 1024)
public class Client implements WebSocketInterface.Listener {

    final WebSocketInterface webSocket;
    private final ProvisioningCipher provisioningCipher;
    private final SecureRandom sr;
    final SocketManager socketManager;
    final WebAPI webApi;

    private final Elita elita;
    
    public Client(Elita elita) {
        this.elita = elita;
        this.webSocket = new WebSocketInterface();
        this.provisioningCipher = new ProvisioningCipher();
        this.sr = new SecureRandom();
        this.socketManager = new SocketManager(this);
        this.webApi = new WebAPI(this);
    }

    public void startup() {
        SslContextFactory scf = new SslContextFactory(true);
        HttpClient httpClient = new HttpClient(scf);
        WebSocketClient holder = new WebSocketClient(httpClient);

        StdErrLog logger = new StdErrLog();
        logger.setLevel(StdErrLog.LEVEL_OFF);
        Log.setLog(logger);

        try {
            webSocket.setListener(this);
            httpClient.start();
            holder.start();
            URI uri = new URI("wss://textsecure-service.whispersystems.org/v1/websocket/provisioning/?agent=OWD&version=5.14.0");
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            holder.connect(webSocket, uri, request);
            Thread.sleep(10000);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void askLocalName() {
        
    }
    
    private void confirmCode(String number, String code, String newPassword, 
        int registrationId, String deviceName) {
        String call = (deviceName  != null) ? "devices" : "accounts";
        String urlPrefix = (deviceName != null) ? "/" : "/code";
        socketManager.authenticate("", "");
        System.err.println("Confirm code");
    }
    
    public void createAccount(ProvisionMessage pm, String deviceName) {
        System.err.println("Creating device "+deviceName);
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String password = new String(b, StandardCharsets.UTF_8);
        password = password.substring(0, password.length()-2);
        int regid = new SecureRandom().nextInt(16384) & 0x3fff;
        webApi.confirmCode(pm.getNumber(),pm.getProvisioningCode(), password, regid, deviceName);
        System.err.println("");
        Account account = new Account(pm.getNumber(), pm.getProvisioningCode());
      //  Account account = new Account();
//          await createAccount(
//        provisionMessage.number,
//        provisionMessage.provisioningCode,
//        provisionMessage.identityKeyPair,
//        provisionMessage.profileKey,
//        deviceName,
//        provisionMessage.userAgent,
//        provisionMessage.readReceipts,
//        { uuid: provisionMessage.uuid }
//      );
//      await clearSessionsAndPreKeys();
//      const keys = await generateKeys();
//      await this.server.registerKeys(keys);
//      await this.confirmKeys(keys);
//      await this.registrationDone();
}
      
    @Override
    public void onReceivedRequest(WebSocketRequestMessage requestMessage) {
        String path = requestMessage.getPath();
        System.out.println("[JVDBG] Got request from path " + path);
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
            String url = "tsdevice:/?uuid=" + uuid + "&pub_key="+ourPubKey;
            System.err.println("URL = "+url);
            elita.setProvisioningURL(url);
        } else if ("/v1/message".equals(path)) {
            try {
                ProvisionEnvelope envelope = ProvisionEnvelope.parseFrom(data);
                ByteString publicKey = envelope.getPublicKey();
                ProvisionMessage pm = provisioningCipher.decrypt(envelope);
                System.err.println("Got pm: "+pm);
                elita.gotProvisionMessage(pm);
            //  const deviceName = await confirmNumber(provisionMessage.number);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
        System.err.println("onClosed()");
    }

    @Override
    public void onConnected() {
        try {
            System.err.println("Connected!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
