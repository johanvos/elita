package com.gluonhq.elita;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.ContactDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.ContactDetails.Avatar;
import signalservice.DeviceMessages.ProvisionMessage;

/**
 *
 * @author johan
 */
public class Elita extends Application {

    StackPane root = new StackPane();
    private Client client;
    private ProvisionMessage provisionResult;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Scene scene = new Scene(root, 350, 350);
        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(scene);
        primaryStage.show();
        startClientFlow();
    }

    private void startClientFlow() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    client = new Client(Elita.this);
                    client.startup();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                }
            }
        };
        t.start();
    }

    public void gotProvisionMessage(ProvisionMessage provisionResult) {
        this.provisionResult = provisionResult;
        askLocalName();
    }

    public void askLocalName() {
        Label l = new Label("Enter the name you want to use for this device:");
        TextField tf = new TextField();
        Button btn = new Button("confirm");
        VBox vbox = new VBox(10, l, tf, btn);
        Platform.runLater(() -> {
            root.getChildren().clear();
            root.getChildren().add(vbox);
        });
        btn.setOnAction(b -> {
            String name = tf.getText();
            try {
                client.createAccount(provisionResult, name);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void setProvisioningURL(String url) {
        Image image = QRGenerator.getImage(url);
        ImageView iv = new ImageView(image);
        Platform.runLater(() -> root.getChildren().add(iv));
    }

    public static void main(String[] args) throws Exception {
        try {
            Security.addProvider(new BouncyCastleProvider());
            Security.setProperty("crypto.policy", "unlimited");
            int maxKeySize = javax.crypto.Cipher.getMaxAllowedKeyLength("AES");
            System.err.println("max AES keysize = "+maxKeySize);
         //  // launch();
            buildContacts();
            readContacts();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static void readContacts() throws FileNotFoundException, IOException {
        File f = new File("/tmp/myin");
        InputStream ois = new FileInputStream(f);
        DeviceContactsInputStream is = new DeviceContactsInputStream(ois);
        DeviceContact dc = is.read();
        while (dc != null) {
            System.err.println("Got contact: "+dc);
            dc = is.read();
        }

        
//        SignalServiceProtos.ContactDetails parseFrom = 
//                SignalServiceProtos.ContactDetails.parseDelimitedFrom(ois);
//        while (parseFrom != null) {
//            System.err.println("\ndetails = " + parseFrom.getAllFields() + "\n");
//            Avatar avatar = parseFrom.getAvatar();
//            System.err.println("avatar = "+ avatar.getAllFields());
//            parseFrom = SignalServiceProtos.ContactDetails.parseDelimitedFrom(is);
//        }
    }
    
    private static void buildContacts() throws FileNotFoundException, IOException {
        ContactDetails.Builder cdb = SignalServiceProtos.ContactDetails.newBuilder();
        cdb.setName("BRAM");
        cdb.setNumber("+32474742283");
        Avatar.Builder ab = SignalServiceProtos.ContactDetails.Avatar.newBuilder();
        ContactDetails cd = cdb.build();
        byte[] b = cd.toByteArray();
        File f = new File("/tmp/cre");
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(b);
    
    }
}
