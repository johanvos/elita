/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import org.whispersystems.signalservice.api.SignalSessionLock;

/**
 *
 * @author johan
 */
public class LockImpl implements SignalSessionLock {

    static private Lock singleton = new Lock() {
        @Override
        public void close() {
            System.err.println("LOCK CLOSED");
        }
    };
    @Override
    public Lock acquire() {
        return singleton;
    }
    
}
