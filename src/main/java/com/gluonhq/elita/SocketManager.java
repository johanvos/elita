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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StdErrLog;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.whispersystems.websocket.messages.WebSocketRequestMessage;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;
import signalservice.DeviceMessages;

/**
 *
 * @author johan
 */
public class SocketManager {

    static final String SERVER_NAME = "textsecure-service.whispersystems.org";

    private Client client;
    private final String url;
    private final String ca;
    private final String version;
    private final String proxyUrl;
    //private WebSocketInterface webSocket;
    private HttpClient httpClient;

    public SocketManager(Client client, String url, String ca, String version, String proxyUrl) {
        this.url = url;
        this.ca = ca;
        this.version = version;
        this.proxyUrl = proxyUrl;
        this.client = client;
        System.err.println("SocketManager constructor called");
    }

    public void authenticate(String username, String password) {
        System.err.println("SocketManager.authenticate called");
        if (username.isEmpty() && password.isEmpty()) {
            System.err.println("SocketManager authenticate was called without credentials");
            return;
        }
        System.err.println("SOCKETMANAGER AUTHENTICATE NYI");

    }

    private void getUnauthenticatedResource() {

    }
    WebSocketInterface unauthenticatedClient = null;

    private WebSocketInterface getUnauthenticatedClient() {
        if (unauthenticatedClient == null) {
            unauthenticatedClient = connectResource("");
        }
        return unauthenticatedClient;
    }

    public WebSocketInterface createProvisioning() {
        WebSocketInterface.Listener wsl = new ProvisioningListener();
        WebSocketInterface answer = connectResource("provisioning/", wsl);
        return answer;
    }

    private WebSocketInterface connectResource(String path) {
        return this.connectResource(path, new WebSocketListener());
    } 
    
    private WebSocketInterface connectResource(String path, WebSocketInterface.Listener wsl) {
        System.err.println("start setting logger");
        StdErrLog logger = new StdErrLog();
        logger.setLevel(StdErrLog.LEVEL_INFO);
        
        Log.setLog(logger);
        System.err.println("done settng logger");
        WebSocketInterface answer = new WebSocketInterface();
        SslContextFactory scf = new SslContextFactory(true);
        httpClient = new HttpClient(scf);
        WebSocketClient holder = new WebSocketClient(httpClient);

        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    answer.setListener(wsl);
                    httpClient.start();
                    holder.start();
                    URI uri = new URI("wss://" + SERVER_NAME + "/v1/websocket/"+path+"?agent=OWD&version=5.14.0");
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
//            cdl.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        return answer;
    }

    int cnt = 1;

    public void fetch(String verb, String path) throws IOException {
        // check authenticated or not
        System.err.println("ready... verb = " + verb);
        WebSocketInterface client = getUnauthenticatedClient();
        System.err.println("ready to send request to client "+client);
        client.sendRequest(cnt++, verb, "v1/config");
    }

    public void fetch(Map<String, String> params) throws IOException {
        String verb = params.getOrDefault("verb", "PUT");
        String path = params.get("path");
        System.err.println("send request to "+cnt+", " + verb+", "+path);
        WebSocketInterface client = getUnauthenticatedClient();
        System.err.println("Ready to send request to client "+client);
        client.sendRequest(cnt, verb, path);
        cnt++;
        System.err.println("Done sending request");
    }

    class WebSocketListener implements WebSocketInterface.Listener {

        private WebSocketInterface parent;
        
        public WebSocketListener() {
            //  this.cdl = cdl;
        }
        
        @Override public void attached (WebSocketInterface wsi) {
            this.parent = wsi;
        }

        @Override
        public void onReceivedRequest(WebSocketRequestMessage requestMessage) {
            String path = requestMessage.getPath();
                 System.err.println("[JVDBG[ normal ORR for path "+path);
            client.provisioningMessageReceived(requestMessage);

        }

        @Override
        public void onReceivedResponse(WebSocketResponseMessage responseMessage) {
            System.err.println("[JVDBG] Got response: " + responseMessage.getStatus());
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
                //      cdl.countDown();
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
