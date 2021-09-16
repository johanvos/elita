/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.gluonhq.elita;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.whispersystems.libsignal.logging.SignalProtocolLogger;

/**
 *
 * @author johan
 */
public class SignalLogger implements SignalProtocolLogger {

    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void log(int priority, String tag, String message) {
        String format = dtf.format(LocalTime.now());
        System.err.println(format + " " + priority + " [" + tag + "] " + message);
    }

}
