/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita.model;

/**
 *
 * @author johan
 */
public class Account {
    
    private String number;
    private String provisioningCode;
    
    public Account(String number, String provisioningCode) {
        this.number = number;
        this.provisioningCode = provisioningCode;
    }
}
