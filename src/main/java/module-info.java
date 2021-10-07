module com.gluonhq.wave {
    requires java.desktop;
    requires java.logging;
    requires javafx.controls;
    requires javafx.swing;
    requires com.google.protobuf;
    requires bcprov.jdk15on;
    requires org.whispersystems.metadata;
    requires org.whispersystems.protocol;
    requires org.whispersystems.service;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.google.zxing;
    requires okhttp3;

    exports com.gluonhq.wave;
    exports com.gluonhq.wave.message;
    exports com.gluonhq.wave.provisioning;
}
