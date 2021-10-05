/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.wave.message;

/**
 *
 * @author johan
 */
public interface MessagingClient {
    
    void gotMessage(String senderUuid, String content, long timestamp);
    
}
