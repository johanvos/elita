/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita.storage;

/**
 *
 * @author johan
 */
public class User {
        
    public static String getUserName() {
        System.err.println("WARNING: fake storage.user.getUserName");
        return "";
    }
    
    public static String getPassword() {
        System.err.println("WARNING: fake storage.user.getPassword");
        return "";
    }
}
