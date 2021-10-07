/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.wave;

import com.gluonhq.elita.SocketManager;
import java.util.logging.Level;
import java.util.logging.Logger;
//import org.eclipse.jetty.client.HttpClient;
//import org.eclipse.jetty.client.api.ContentResponse;
//import org.eclipse.jetty.client.api.Request;
//import org.eclipse.jetty.client.util.StringContentProvider;
//import org.eclipse.jetty.http.HttpHeader;
//import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 *
 * @author johan
 */
public class Networking {}
/*
    
    static final String AGENT="Signal-Desktop/5.14.0 Linux";

    public static ContentResponse httpRequest (String url, String method, String path, String body, String auth) {
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
        if (auth != null) {
            request.header(HttpHeader.AUTHORIZATION, "Basic " + auth);
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
            System.err.println("Got response from "+ request.getURI()+" with statuscode "+response.getStatus());
            httpClient.stop();
        } catch (Exception ex) {
           ex.printStackTrace();
        }
        return response;        
    }

}
*/