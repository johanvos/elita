/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.test.elita;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
//import org.whispersystems.websocket.messages.InvalidMessageException;
//import org.whispersystems.websocket.messages.WebSocketMessage;
//import org.whispersystems.websocket.messages.WebSocketMessageFactory;
//import org.whispersystems.websocket.messages.protobuf.ProtobufWebSocketMessageFactory;

/**
 *
 * @author johan
 */
public class MacTest {

    @Test
    public void dummy() {
        assertTrue(1 + 1 == 2);
    }

    @Test
    public void macCheck() {
        ECKeyPair ourKeyPair = Curve.generateKeyPair();
    }

    @Test
    public void reverseMessage() {
        byte[] in = new byte[]{8, 1, 18, (byte) 238, 3, 10, 3, 80, 85, 84, 18, 18, 47, 118, 49, 47,
            100, 101, 118, 105, 99, 101, 115, 47, 52, 52, 50, 57, 52, 55, 26, (byte) 160, 2,
            123, 34, 99, 97, 112, 97, 98, 105, 108, 105, 116, 105, 101, 115, 34, 58, 123, 34, 97, 110, 110, 111, 117, 110, 99, 101, 109, 101, 110, 116, 71, 114, 111, 117, 112, 34, 58, 116, 114, 117, 101, 44, 34, 103, 118, 50, 45, 51, 34, 58, 116, 114, 117, 101, 44, 34, 103, 118, 49, 45, 109, 105, 103, 114, 97, 116, 105, 111, 110, 34, 58, 116, 114, 117, 101, 44, 34, 115, 101, 110, 100, 101, 114, 75, 101, 121, 34, 58, 116, 114, 117, 101, 125,
            44, 34, 102, 101, 116, 99, 104, 101, 115, 77, 101, 115, 115, 97, 103, 101, 115, 34, 58, 116, 114, 117, 101,
            44, 34, 110, 97, 109, 101, 34, 58, 34, 67, 105, 69, 70, 98, 88, 78, 48, 48, 107, 77, 86, 105, 97, 52, 120, 107, 55, 106, 66, 66, 57, 78, 52, 43, 114, 102, 49, 54, 73, 120, 120, 75, 106, 50, 76, 75, 57, 68, 114, 122, 71, 68, 119, 84, 65, 103, 83, 69, 80, 72, 114, 115, 66, 77, 90, 56, 106, 97, 67, 67, 56, 43, 81, 79, 56, 52, 75, 115, 103, 65, 97, 66, 70, 79, 101, 89, 122, 111, 61, 34,
            44, 34, 114, 101, 103, 105, 115, 116, 114, 97, 116, 105, 111, 110, 73, 100, 34, 58, 50, 48, 54, 49,
            44, 34, 115, 117, 112, 112, 111, 114, 116, 115, 83, 109, 115, 34, 58, 102, 97, 108, 115, 101,
            44, 34, 117, 110, 114, 101, 115, 116, 114, 105, 99, 116, 101, 100, 85, 110, 105, 100, 101, 110, 116, 105, 102, 105, 101, 100, 65, 99, 99, 101, 115, 115, 34, 58, 102, 97, 108, 115, 101, 125, 32, 1, 42, 68, 97, 117, 116, 104, 111, 114, 105, 122, 97, 116, 105, 111, 110, 58, 66, 97, 115, 105, 99, 32, 75, 122, 77, 121, 78, 68, 99, 48, 79, 84, 107, 50, 78, 84, 85, 51, 79, 107, 82, 79, 84, 49, 111, 119, 100, 69, 120, 109, 75, 122, 78, 70, 81, 87, 49, 86, 85, 110, 100, 118, 97, 106, 74, 121, 99, 87, 99, 61, 42, 44, 99, 111, 110, 116, 101, 110, 116, 45, 116, 121, 112, 101, 58, 97, 112, 112, 108, 105, 99, 97, 116, 105, 111, 110, 47, 106, 115, 111, 110, 59, 32, 99, 104, 97, 114, 115, 101, 116, 61, 117, 116, 102, 45, 56, 42, 38, 117, 115, 101, 114, 45, 97, 103, 101, 110, 116, 58, 83, 105, 103, 110, 97, 108, 45, 68, 101,
            115, 107, 116, 111, 112, 47, 53, 46, 49, 52, 46, 48, 32, 76, 105, 110, 117, 120, 42,
            18, 120, 45, 115, 105, 103, 110, 97, 108, 45, 97, 103, 101, 110, 116, 58, 79, 87, 68};

//        WebSocketMessageFactory factory = new ProtobufWebSocketMessageFactory();
//        try {
//            WebSocketMessage parseMessage = factory.parseMessage(in, 0, in.length);
//            System.err.println("PM = " + parseMessage);
//        } catch (InvalidMessageException ex) {
//            Logger.getLogger(MacTest.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }
}
