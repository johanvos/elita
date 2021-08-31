/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
        int registrationId, String deviceName) throws JsonProcessingException {
        String call = (deviceName  != null) ? "devices" : "accounts";
        String urlPrefix = (deviceName != null) ? "/" : "/code";
        this.socketManager.authenticate("", "");
        System.err.println("Confirm code");

        String body = getDeviceMapData(deviceName, registrationId);
        System.err.println("body = "+body);
        String username = number;
        String pwd = newPassword;
        Map params = new HashMap();
        List<String> headers = new LinkedList();
        String authbase = username+":"+pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        System.err.println("result of "+ authbase+" conv = "+ basicAuth);
        headers.add("Authorization:Basic "+basicAuth);
        headers.add("content-type:application/json;charset=utf-8");
        headers.add("User-Agent:Signal-Desktop/5.14.0 Linux");
        headers.add("x-signal-agent:OWD");
      
        params.put("path", "/v1/" + call+"/"+code);
        params.put("verb", "PUT");
        params.put("body", body);
        params.put("httpType", "PUT");
        params.put("responseType", "json");
        params.put("urlParameters", urlPrefix + code);
        try {
            this.socketManager.fetch(params, headers);
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
    
    private String getDeviceMapData(String name, int registrationId) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.put("announcementGroup", true);
        capabilities.put("gv2-3", true);
        capabilities.put("gv1-migration", true);
        capabilities.put("senderKey", true);

        ObjectNode jsonData = mapper.createObjectNode();
        System.err.println("DOOH");
      //  Thread.dumpStack();
        jsonData.set("capabilities", capabilities);
        jsonData.put("fetchesMessages", true);
        jsonData.put("name", name);
        jsonData.put("registrationId", registrationId);
        jsonData.put("supportsSms", false);
        jsonData.put("unrestrictedUnidentifiedAccess", false);
        String answer = mapper.writeValueAsString(jsonData);
        return answer;

    }
}
