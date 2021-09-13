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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.client.api.ContentResponse;
import org.whispersystems.signalservice.internal.push.PreKeyResponse;
import org.whispersystems.signalservice.internal.push.PreKeyResponseItem;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.websocket.messages.WebSocketResponseMessage;

/**
 *
 * @author johan
 */
public class WebAPI {
    ObjectMapper rootMapper = new ObjectMapper();

    private final Client client;
    private final String url;
    private SocketManager socketManager;
    
    private String uuid;
    private String number;
    private String pwd;
    private int deviceId;
    private String basicAuth;
    
    public WebAPI (Client c, String url) {
        this.client = c;
        this.url = url;
        System.err.println("WebAPI constructor called");
    }
    
    public void initialize() {
        System.err.println("Initialize WebAPI");
    }

    // TODO this needs to be in a storage
    public String getMyUuid() {
        return this.uuid;
    }
    public String getMyNumber() {
        return this.number;
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

    /**
     * Creates a Authorization:Basic header in case we have a uuid
     */
    Optional<String> createBasicAuthHeader() {
        if (uuid != null) {
            String authbase = uuid + "." + deviceId + ":" + pwd;
            String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
            return Optional.of("Authorization:Basic " + basicAuth);
        }
        return Optional.empty();
    }
    
    public void getConfig() {
        getConfig(null);
    }

    public void getConfig(Consumer<RemoteConfigResponse> callback) {
        Map params = new HashMap();
        params.put("path", "/v1/config");
        params.put("verb", "GET");
        params.put("responseType", "json");
        List<String> headers = getDefaultHeaders();
        System.err.println("getConfig asked, uuid = "+uuid);
        Optional<String> auth = createBasicAuthHeader();
        if (auth.isPresent()) headers.add(auth.get());
        try {
            Consumer<WebSocketResponseMessage> f = message -> {
                if ((message.getStatus() == 200) &&(message.getBody().isPresent())) {
                    try {
                        String body = new String(message.getBody().get(), StandardCharsets.UTF_8);
                        System.err.println("message message = " + body);
                        RemoteConfigResponse remoteConfig = JsonUtil.fromJson(body, RemoteConfigResponse.class);
                        if (callback != null) {
                            callback.accept(remoteConfig);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Logger.getLogger(WebAPI.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    System.err.println("getConfig response = " + message.getStatus());
                }
            };
            this.socketManager.fetch(params, headers, f);
 
        } catch (IOException ex) {
            Logger.getLogger(WebAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
  //  private String basicAuth;
    public void confirmCode(String number, String code, String newPassword, 
        int registrationId, String deviceName, String uuid) throws JsonProcessingException {
        this.uuid = uuid;
        this.number = number;
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
        this.basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        System.err.println("result of "+ authbase+" conv = "+ basicAuth);
        System.err.println("BA1 = "+basicAuth);
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
    
    
    void getGroupCredentials(long startDate, long endDate) throws IOException {
        List<String> headers = getDefaultHeaders();
        Optional<String> auth = createBasicAuthHeader();
        if (auth.isPresent()) headers.add(auth.get());
        Map<String, String> params = new HashMap<>();
        params.put("path", "/v1/certificate/group/"+startDate+"/"+endDate);
        params.put("verb", "GET");
        params.put("responseType", "json");
        this.socketManager.fetch(params, headers, null);
    }
    
    void registerSupportForUnauthenticatedDelivery() throws IOException {
        List<String> headers = getDefaultHeaders();
        Optional<String> auth = createBasicAuthHeader();
        if (auth.isPresent()) headers.add(auth.get());
        Map<String, String> params = new HashMap<>();
        params.put("path", "/v1/devices/unauthenticated_delivery");
        params.put("verb", "PUT");
        params.put("responseType", "json");
        this.socketManager.fetch(params, headers, null);
    }
    
    PreKeyResponse getKeysForIdentifier() throws IOException {
        List<String> headers = getDefaultHeaders();
        Optional<String> auth = createBasicAuthHeader();
        if (auth.isPresent()) headers.add(auth.get());
        Map<String, String> params = new HashMap<>();
        params.put("path", "/v2/keys/"+uuid+"/*");
        params.put("verb", "GET");
        params.put("responseType", "json");
        ContentResponse response = this.fetchHttp("GET", "/v2/keys/"+uuid+"/*", null); 
        String responseText = response.getContentAsString();
        PreKeyResponse preKeys = JsonUtil.fromJson(responseText, PreKeyResponse.class);
        return preKeys;
    }

    void registerCapabilities() throws IOException {
        List<String> headers = getDefaultHeaders();
        Optional<String> auth = createBasicAuthHeader();
        if (auth.isPresent()) headers.add(auth.get());
        Map<String, String> params = new HashMap<>();
        params.put("path", "/v1/devices/capabilities");
        params.put("verb", "PUT");
        params.put("responseType", "json");
        String body = rootMapper.writeValueAsString(createDefaultCapabilities());
        params.put("body", body);
        this.socketManager.fetch(params, headers, null);
    }

    void fetch(String url, String verb, String jsonData) throws IOException {
        fetch (url, verb, jsonData, this.socketManager);
    }
    
    void fetch(String url, String verb, String jsonData, SocketManager sm) throws IOException {
        List<String> headers = new LinkedList<>();
        headers.add("Authorization:Basic "+basicAuth);
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

    ContentResponse fetchHttp(String method, String path, String jsonData) {
        System.err.println("[SEND] fetchhttp: "+method+" "+path);
        String authbase = uuid+"."+deviceId+":"+pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        ContentResponse response = this.socketManager.httpRequest(method, path, jsonData, basicAuth);
        return response;
    }
    
    ContentResponse fetchCdnHttp(String method, String path, String jsonData) {
        System.err.println("[SEND] fetchhttp: "+method+" "+path);
        String authbase = uuid+"."+deviceId+":"+pwd;
        String basicAuth = Base64.getEncoder().encodeToString(authbase.getBytes());
        ContentResponse response = this.socketManager.httpRequest("https://cdn2.signal.org", method, path, jsonData, null);
        System.err.println("got response: "+response);
        return response;
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

    private ObjectNode createDefaultCapabilities() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.put("announcementGroup", true);
        capabilities.put("gv2-3", true);
        capabilities.put("gv1-migration", true);
        capabilities.put("senderKey", true);
        return capabilities;
    }

}
