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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;

/**
 *
 * @author johan
 */
public class WebAPI {
    
    private final Client client;
    private final String url;
    private SocketManager socketManager;
    
    private String uuid;
    private String pwd;
    private int deviceId;
    
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

    public void onOffline() {
        this.socketManager.onOffline();
    }

    public void onOnline() {
        this.socketManager.onOnline();
    }

    public void getConfig() {
        Map params = new HashMap();
        params.put("path", "/v1/config");
        params.put("verb", "GET");
        params.put("responseType", "json");
        List<String> headers = getDefaultHeaders();
        System.err.println("getConfig asked, uuid = "+uuid);
        if (uuid != null) {
            String authbase = uuid+"."+deviceId+":"+pwd;
            String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
            headers.add("Authorization:Basic "+basicAuth);
        }
        try {
            this.socketManager.fetch(params, headers, null);
        } catch (IOException ex) {
            Logger.getLogger(WebAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
  //  private String basicAuth;
    public void confirmCode(String number, String code, String newPassword, 
        int registrationId, String deviceName, String uuid) throws JsonProcessingException {
        this.uuid = uuid;
        String call = (deviceName  != null) ? "devices" : "accounts";
        String urlPrefix = (deviceName != null) ? "/" : "/code";
        this.socketManager.authenticate("", "");
        System.err.println("Confirm code");

        String body = getDeviceMapData(deviceName, registrationId);
        System.err.println("body = "+body);
        String username = number;
        this.pwd = newPassword;
        Map params = new HashMap();
        List<String> headers = new LinkedList();
        String authbase = username+":"+pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        System.err.println("result of "+ authbase+" conv = "+ basicAuth);
        System.err.println("BA1 = "+basicAuth);
     //   basicAuth="OWU2YzgxYjItYmJhZC00NzE3LTg1MDYtYjhmMmFiYzk0YTUyLjE3OmJ4OE9BQ1JjNXZ1U3ZOREhhYjlGYnc=";
   //     this.basicAuth = basicAuth;
        System.err.println("BA2 = "+basicAuth);
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
        CountDownLatch cdl = new CountDownLatch(1);
        Consumer<WebSocketResponseMessage> f = message -> {
            System.err.println("Result for registerDevice got in!");
            if (message.getStatus() == 200) {
                String res = new String(message.getBody().get(), StandardCharsets.UTF_8);
                System.err.println("result: "+res);
                int c = res.indexOf(":");
                String did = res.substring(c+1, res.length()-1);
                this.deviceId = Integer.parseInt(did);
                System.err.println("did = "+deviceId);
            }
           cdl.countDown();
        };
        try {
            this.socketManager.fetch(params, headers, f);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
         try {
                cdl.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(WebAPI.class.getName()).log(Level.SEVERE, null, ex);
            }
    }
    
    void fetch(String url, String verb, String jsonData) throws IOException {
        fetch (url, verb, jsonData, this.socketManager);
    }
    
    void fetch(String url, String verb, String jsonData, SocketManager sm) throws IOException {
        List<String> headers = new LinkedList<>();
       //    headers.add("Authorization:Basic "+basicAuth);
        headers.add("content-type:application/json;charset=utf-8");
        headers.add("User-Agent:Signal-Desktop/5.14.0 Linux");
        headers.add("x-signal-agent:OWD");
      
        Map<String, String> params = new HashMap<>();
        params.put("path", url);
        params.put("verb", "PUT");
        params.put("body", jsonData);
        sm.fetch(params, headers, c -> {System.err.println("SENT KEYS");});
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

    void fetchHttp(String method, String path, String jsonData) {
        String authbase = uuid+"."+deviceId+":"+pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        System.err.println("result of "+ authbase+" conv = "+ basicAuth);
        System.err.println("BA1 = "+basicAuth);
        this.socketManager.httpRequest(method, path, jsonData, basicAuth);
    }
    
    List<String> getDefaultHeaders() {
        List<String> answer = new LinkedList<>();
        answer.add("content-type:application/json;charset=utf-8");
        answer.add("User-Agent:Signal-Desktop/5.14.0 Linux");
        answer.add("x-signal-agent:OWD");
        return answer;
    }

    void authenticate() {
        socketManager.authenticate(uuid+"."+deviceId, pwd);
    }
}
