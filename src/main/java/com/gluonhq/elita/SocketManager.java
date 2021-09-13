/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope;
import org.whispersystems.util.Base64;
import org.whispersystems.websocket.messages.WebSocketRequestMessage;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;

/**
 *
 * @author johan
 */
public class SocketManager {

    static final String SERVER_NAME = "textsecure-service.whispersystems.org";
    static final String AGENT="Signal-Desktop/5.14.0 Linux";
    static final String UNIDENTIFIED_SENDER_TRUST_ROOT = "BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF";
    private final Client client;
    private final String url;
   // private HttpClient httpClient;
    
    private String username = null;
    private String password = null;
    
    WebSocketInterface unauthenticatedClient = null;
    WebSocketInterface authenticatedClient = null;
    
    private boolean offline = false;
    
        private final Map<Long, Consumer<WebSocketResponseMessage>> pending = new HashMap<>();

    long requestCounter = 0;
  public static CertificateValidator getCertificateValidator() {
    try {
      ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
      return new CertificateValidator(unidentifiedSenderTrustRoot);
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }
  }

    public SocketManager(Client client, String url, String ca, String version, String proxyUrl) {
        this.url = url;
        this.client = client;
        System.err.println("SocketManager constructor called");
    }

    public void onOffline() {
        System.err.println("[SocketManager] onOffline called");
        this.offline = true;
        if (this.authenticatedClient != null) this.authenticatedClient.stopSession();
        if (this.unauthenticatedClient != null) this.unauthenticatedClient.stopSession();
        this.authenticatedClient = null;
        this.unauthenticatedClient = null;
    }
  
    public void onOnline() {
        System.err.println("[SocketManager] onOnline called");
        this.offline = false;

        if (this.username != null && this.password != null) {
            authenticate(this.username, this.password);
        }
    }

    public void authenticate(String myUsername, String myPassword) {
        System.err.println("SocketManager.authenticate called");
        if (myUsername.isEmpty() && myPassword.isEmpty()) {
            System.err.println("SocketManager authenticate was called without credentials");
            return;
        }
        this.username = myUsername;
        this.password = myPassword;
        this.authenticatedClient = null;
    }

    private WebSocketInterface getUnauthenticatedClient() {
        if (unauthenticatedClient == null) {
            unauthenticatedClient = connectResource("", false);
        }
        return unauthenticatedClient;
    }

    private WebSocketInterface getAuthenticatedClient() {
        if (authenticatedClient == null) {
            authenticatedClient = connectResource("", true);
        }
        return authenticatedClient;
    }
    
    public WebSocketInterface createProvisioning() {
        WebSocketInterface.Listener wsl = new ProvisioningListener();
        WebSocketInterface answer = connectResource("provisioning/", wsl, false);
        return answer;
    }

    private WebSocketInterface connectResource(String path, boolean auth) {
        return this.connectResource(path, new WebSocketListener(), auth);
    } 
    
//    private WebSocketInterface connectResource(String path, WebSocketInterface.Listener wsl) {
//        return connectResource(path, wsl, false);
//    }
    
    private WebSocketInterface connectResource(String path, WebSocketInterface.Listener wsl, boolean auth) {
        System.err.println("start setting logger");
        StdErrLog logger = new StdErrLog();
        logger.setLevel(StdErrLog.LEVEL_INFO);
        
        Log.setLog(logger);
        System.err.println("done settng logger");
        WebSocketInterface answer = new WebSocketInterface();
        SslContextFactory scf = new SslContextFactory(true);
        HttpClient httpClient = new HttpClient(scf);
        httpClient.setUserAgentField(new HttpField(HttpHeader.USER_AGENT,AGENT));
        WebSocketClient holder = new WebSocketClient(httpClient);
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    answer.setListener(wsl);
                    httpClient.start();
                    holder.start();
                    String url = "wss://" + SERVER_NAME + "/v1/websocket/"+path+"?agent=OWD&version=5.14.0";
                    if (auth) {
                        url = url+"&login="+username+"&password="+password;
                    }
                    URI uri = new URI(url);
                    ClientUpgradeRequest request = new ClientUpgradeRequest();
                    holder.connect(answer, uri, request);
                    System.err.println("Websocket connected for uri = "+uri);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        t.start();
        try {
            answer.waitUntilConnected(10);
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        return answer;
    }

    public long fetch(String verb, String path) throws IOException {

        long answer = requestCounter;
        System.err.println("[SEND] fetchws: "+verb+" v1/config, req "+answer);
        requestCounter++;
        System.err.println("ready... verb = " + verb);
        WebSocketInterface client = getUnauthenticatedClient();
        System.err.println("ready to send request to client " + client);
        client.sendRequest(answer, verb, "v1/config");
        return answer;
    }

    public long fetch(Map<String, String> params) throws IOException {
        return fetch (params, new LinkedList<String>(), null);
    }

    private boolean isAuthenticated(List<String> headers) {
        boolean answer = headers.stream().anyMatch(h -> h.startsWith("Authorization"));
        System.err.println("Does request header contain authorization? " + answer);
        if ((username == null) || (password == null)) {
            answer = false;
            System.err.println("we're not authenticated yet");
        }
        return answer;
    }
    
    public long fetch(Map<String, String> params, List<String> headers, Consumer<WebSocketResponseMessage> callback) throws IOException {

        long answer = requestCounter;
        requestCounter++;
        if (callback != null) {
            pending.put(answer, callback);
        }
        String verb = params.getOrDefault("verb", "PUT");
        String path = params.get("path");
        String body = params.get("body");
        System.err.println("[SEND] fetchws: "+verb+" "+path+", req "+answer);

        System.err.println("send request to "+answer+", " + verb+", "+path);
        WebSocketInterface client = (isAuthenticated (headers) ? 
                getAuthenticatedClient() : getUnauthenticatedClient());
        System.err.println("Ready to send request to client "+client);
        client.sendRequest(answer, verb, path, headers, body == null? null: body.getBytes(StandardCharsets.UTF_8));
        System.err.println("Done sending request");
        return answer;
    }
    
    /**
     * Synchronous call
     * @param method
     * @param path
     * @param body
     * @param ba
     * @return 
     */
    public ContentResponse httpRequest (String method, String path, String body, String ba) {
        return httpRequest(this.url, method, path, body, ba);
    }
    
    public ContentResponse httpRequest (String url, String method, String path, String body, String ba) {
      SslContextFactory scf = new SslContextFactory(true);
        HttpClient httpClient = new HttpClient(scf);
        try {
            httpClient.start();
        } catch (Exception ex) {
            Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
        }
//        String[] headerArr = new String[headers.size()];
//        headers.toArray(headerArr);
System.err.println("create request to "+url);
        Request request = httpClient.newRequest(url)
                .method(method).path(path);
        
            request.agent(AGENT);
            request.header("X-Signal-Agent", "OWD");
        if (ba != null) {
            request.header(HttpHeader.AUTHORIZATION, "Basic " + ba);
        }
        if (body != null){
            request.content(new StringContentProvider(body), "application/json");
        }
        System.err.println("sending "+request);
        System.err.println("method = "+request.getMethod());
        System.err.println("agent = " + request.getAgent());
        System.err.println("path = "+request.getPath());
        System.err.println("fp = "+request.getHost());
        System.err.println("proto = "+request.getScheme());
        System.err.println("query = "+request.getQuery());
        System.err.println("headers = "+request.getHeaders());
        System.err.println("URI = "+request.getURI());
        ContentResponse response = null;
        try {
            response = request.send();
            System.err.println("Got response from "+ request.getURI());
//            System.err.println("got response: "+response);
//            System.err.println("RESP " + response.getContentAsString());
            httpClient.stop();
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        return response;        
    }

    class WebSocketListener implements WebSocketInterface.Listener {

        private WebSocketInterface parent;
        
        public WebSocketListener() {
        }
        
        @Override public void attached (WebSocketInterface wsi) {
            this.parent = wsi;
        }

        @Override
        public void onReceivedRequest(WebSocketRequestMessage requestMessage) {
            String path = requestMessage.getPath();
            System.err.println("[JVDBG[ normal OnReceivedRequest for path "+path+": "+ requestMessage);
            if (requestMessage.getBody().isPresent()) {
                System.err.println("[JVDBG] WebSocket Request body present " );
                try {
                    SignalServiceEnvelope sse = new SignalServiceEnvelope(requestMessage.getBody().get(),"foo", false);
                    System.err.println("[JVDBG] got sse: source = "+sse.getSourceIdentifier()+ 
                            ", dest = "+sse.getUuid()+", type = "+sse.getType());
                    SignalServiceContent content = mydecrypt(sse);
                    System.err.println("decrypted: "+content);
                    if (content.getSyncMessage().isPresent()) {
                        SignalServiceSyncMessage sssm = content.getSyncMessage().get();
                        System.err.println("REQ ? " + sssm.getRequest());
                        System.err.println("CONTACTS? " + sssm.getContacts());
                        System.err.println("GROUPS? " + sssm.getGroups());
                        System.err.println("CONFIG? " + sssm.getConfiguration());
                        System.err.println("STICKER? " + sssm.getStickerPackOperations());
                                                client.processSyncMessage(sssm);

                        if (sssm.getStickerPackOperations().isPresent()) {
                            List<StickerPackOperationMessage> spom = sssm.getStickerPackOperations().get();
                            System.err.println("spom = " + spom);
                        }
                    }
                parent.sendResponse(requestMessage.getRequestId(), 200, "OK", "world!".getBytes());
                    System.err.println("ACKED!");
                } catch (InvalidProtocolBufferException ex) {
                    Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvalidVersionException ex) {
                    Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }           
// client.provisioningMessageReceived(requestMessage);

        }
        
        
        SignalServiceContent mydecrypt(SignalServiceEnvelope sse) throws Exception {
            SignalServiceCipher cipher = new SignalServiceCipher(client.getSignalServiceAddress(),
                             client.getSignalServiceDataStore(),
                             getCertificateValidator());
            SignalServiceContent content = cipher.decrypt(sse);
            return content;
        }

        /**
         * When a response is received, the registered function for the corresponding
         * request will be invoked synchronously.
         * @param responseMessage 
         */
        @Override
        public void onReceivedResponse(WebSocketResponseMessage responseMessage) {
            System.err.println("[JVDBG] Got response with status " + responseMessage.getStatus()+
                    " and requestId = "+responseMessage.getRequestId());
            long id = responseMessage.getRequestId();
            if (pending.containsKey(id)) {
                Consumer f = pending.get(id);
                f.accept(responseMessage);
                pending.remove(id);
            }
            System.err.println("Message = " + responseMessage);
            if (responseMessage.getBody().isPresent()) {
                System.err.println("[JVDBG] Got response body: " + new String(responseMessage.getBody().get()));
            }

        }

        @Override
        public void onClosed() {
            System.err.println("[Client] WebSocket NORMAL onClosed() called");
        }

        @Override
        public void onConnected() {
            try {
                System.err.println("[Client] WebSocket onConnected called");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    class ProvisioningListener implements WebSocketInterface.Listener {

        private WebSocketInterface webSocket;

        ProvisioningListener() {
        }
        
        @Override public void attached(WebSocketInterface webSocket) {
            this.webSocket = webSocket;
        }

        @Override
        public void onReceivedRequest(WebSocketRequestMessage requestMessage) {
            System.err.println("prov oRR");
            client.provisioningMessageReceived(requestMessage);

            try {
                webSocket.sendResponse(requestMessage.getRequestId(), 200, "OK", "world!".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onReceivedResponse(WebSocketResponseMessage responseMessage) {
            System.err.println("[JVDBG] provGot response: " + responseMessage.getStatus());

            if (responseMessage.getBody().isPresent()) {
                System.err.println("[JVDBG] provGot response body: " + new String(responseMessage.getBody().get()));
            }
        }

        @Override
        public void onClosed() {
            System.err.println("[Client] provWebSocket PL onClosed() called");
        }

        @Override
        public void onConnected() {
            try {
                System.err.println("[Client] provWebSocket onConnected called");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
