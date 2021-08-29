/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
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
    
    public void authenticate (String username, String password) {
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
//            System.err.println("created uc, wait 5 s");
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException ex) {
//                Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
//            }
        }
        return unauthenticatedClient;
    }
    
    
    private WebSocketInterface connectResource (String path) {
        WebSocketInterface answer = new WebSocketInterface();
        SslContextFactory scf = new SslContextFactory(true);
        httpClient = new HttpClient(scf);
        WebSocketClient holder = new WebSocketClient(httpClient);
        StdErrLog logger = new StdErrLog();
        logger.setLevel(StdErrLog.LEVEL_INFO);
        Log.setLog(logger);
        CountDownLatch cdl = new CountDownLatch(1);
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    WebSocketListener listener = new WebSocketListener(cdl);
                    answer.setListener(listener);
                    httpClient.start();
                    holder.start();
                    URI uri = new URI("wss://" + SERVER_NAME + "/v1/websocket/?agent=OWD&version=5.14.0");
                    ClientUpgradeRequest request = new ClientUpgradeRequest();
                    holder.connect(answer, uri, request);
                    System.err.println("Websocket connected");
                    //   webSocket.sendRequest(1, "GET", "/v1/websocket/provisioning");
                    //    Thread.sleep(10000);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        t.start();
        try {
            cdl.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
        }

        return answer;
    }

    int cnt;
    
    public void fetch(String verb, String path) throws IOException {
        // check authenticated or not
        System.err.println("ready... verb = "+verb);
        getUnauthenticatedClient().sendRequest(cnt++, verb, "v1/config");
    }
    
    public void fetch(Map<String, String> params) throws IOException {
        String verb = params.getOrDefault("verb", "PUT");
        String path = params.get("path");
        getUnauthenticatedClient().sendRequest(cnt++, verb, path);

    }
    
    class WebSocketListener implements WebSocketInterface.Listener {

        private CountDownLatch cdl;
        
        public WebSocketListener(CountDownLatch cdl) {
            this.cdl = cdl;
        }
        @Override
        public void onReceivedRequest(WebSocketRequestMessage requestMessage) {
            String path = requestMessage.getPath();
            System.out.println("[JVDBG] Got request from path " + path);
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
                cdl.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
