/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author johan
 */
public class WebAPI {
    
    private final Client client;
    public WebAPI (Client c) {
        this.client = c;
    }
    
    public void confirmCode(String number, String code, String newPassword, 
        int registrationId, String deviceName) {
        String call = (deviceName  != null) ? "devices" : "accounts";
        String urlPrefix = (deviceName != null) ? "/" : "/code";
        client.socketManager.authenticate("", "");
        System.err.println("Confirm code");
        Map params = new HashMap();
        params.put("httpType", "PUT");
        params.put("responseType", "json");
        params.put("urlParameters", urlPrefix + code);
        try {
            _ajax(params);
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
        System.err.println("PromiseAjax");
        client.socketManager.fetch("PUT", url);
    }
}
