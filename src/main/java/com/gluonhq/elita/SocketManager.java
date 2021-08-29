/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import java.io.IOException;

/**
 *
 * @author johan
 */
public class SocketManager {
    
    private Client client;
    private final String url;
    private final String ca;
    private final String version;
    private final String proxyUrl;
    
    public SocketManager(Client client, String url, String ca, String version, String proxyUrl) {
        this.url = url;
        this.ca = ca;
        this.version = version;
        this.proxyUrl = proxyUrl;
        this.client = client;
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
    
    public void fetch(String verb, String path) throws IOException {
        client.webSocket.sendRequest(1, verb, path);
    }
}
