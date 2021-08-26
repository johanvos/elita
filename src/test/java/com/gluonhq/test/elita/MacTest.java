/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.test.elita;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;

/**
 *
 * @author johan
 */
public class MacTest {
    
    @Test
    public void dummy() {
        assertTrue(1+1== 2);
    }
    
    @Test
    public void macCheck() {
        ECKeyPair ourKeyPair = Curve.generateKeyPair();
    }
    
}
