/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public class WebAPI {
    
    private final Client client;
    private final String url;
    private SocketManager socketManager;
    
    public WebAPI (Client c, String url) {
        this.client = c;
        this.url = url;
        System.err.println("WebAPI constructor called");
    }
    
    public void initialize() {
        System.err.println("Initialize WebAPI");
    }
    
    public void provision() {
        this.socketManager.createProvisioning();
    }
    
    public SocketManager connect (String username, String password) {
        System.err.println("WebAPI connect called with url "+url);
        this.socketManager = new SocketManager(client, url, null, "5.14.0", null);
        socketManager.authenticate(username, password);
        return socketManager;
    }

    public void getConfig() {
        Map params = new HashMap();
        params.put("path", "/v1/config");
        params.put("verb", "GET");
        params.put("responseType", "json");
        try {
            this.socketManager.fetch(params);
        } catch (IOException ex) {
            Logger.getLogger(WebAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void confirmCode(String number, String code, String newPassword, 
        int registrationId, String deviceName) {
        String call = (deviceName  != null) ? "devices" : "accounts";
        String urlPrefix = (deviceName != null) ? "/" : "/code";
        this.socketManager.authenticate("", "");
        System.err.println("Confirm code");
        Map params = new HashMap();
        params.put("path", "v1/" + call);
        params.put("verb", "PUT");
        params.put("httpType", "PUT");
        params.put("responseType", "json");
        params.put("urlParameters", urlPrefix + code);
        try {
            this.socketManager.fetch(params);
         //   _ajax(params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void _ajax(Map params) throws IOException {        
         _outerAjax(null, params);
    }
    
    private void _outerAjax(String url, Map params) throws IOException {
        _retryAjax(url, params);
    }
    
    private void _retryAjax(String url, Map params) throws IOException {
        _promiseAjax(url, params);
    }
    
    private void _promiseAjax(String url, Map params) throws IOException {
        if (url == null) {
            url = params.get("host")+"/"+params.get("path");
        }
        System.err.println("PromiseAjax, url = "+url);
        this.socketManager.fetch("PUT", url);
    }
}
