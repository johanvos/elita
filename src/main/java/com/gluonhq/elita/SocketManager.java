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
    
    public SocketManager(Client client) {
        this.client = client;
    }
    
    public void authenticate (String username, String password) {
        if (username.isEmpty() && password.isEmpty()) {
            System.err.println("SocketManager authenticate was called without credentials");
            return;
        }
        System.err.println("SOCKETMANAGER AUTHENTICATE NYI");
        
    }
    
    public void fetch(String verb, String path) throws IOException {
        client.webSocket.sendRequest(1, verb, path);
    }
}
