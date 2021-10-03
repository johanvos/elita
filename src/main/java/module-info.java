module com.gluonhq.wave {
    requires java.desktop;
    requires java.logging;
    requires javafx.controls;
    requires javafx.swing;
    requires com.google.common;
    requires com.google.protobuf;
    requires bcprov.jdk15on;
    requires org.eclipse.jetty.client;
    requires org.eclipse.jetty.http;
    requires org.eclipse.jetty.util;
    requires org.eclipse.jetty.websocket.api;
    requires org.eclipse.jetty.websocket.client;
    requires websocket.resources;
    requires signal.metadata.java;
    requires signal.protocol.java;
    requires signal.service.java;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.google.zxing;
    requires okhttp3;

    exports com.gluonhq.wave;
    exports com.gluonhq.wave.message;
    exports com.gluonhq.wave.provisioning;
}
