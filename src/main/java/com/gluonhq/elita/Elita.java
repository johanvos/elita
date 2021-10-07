package com.gluonhq.elita;

import com.gluonhq.wave.QRGenerator;
import com.gluonhq.wave.WaveManager;
import com.gluonhq.wave.provisioning.ProvisioningManager;
import com.gluonhq.wave.provisioning.ProvisioningClient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.List;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;

/**
 *
 * @author johan
 */
public class Elita extends Application  implements ProvisioningClient {

    final BorderPane bp = new BorderPane();
    final StackPane root = new StackPane();
    private Client client;
    private final static SignalProtocolStoreImpl signalProtocolStore = new SignalProtocolStoreImpl();
    private String number;
    private final WaveManager waveManager = WaveManager.getInstance();

    @Override
    public void start(Stage primaryStage) throws Exception {
        SignalLogger logger = new SignalLogger();
        SignalProtocolLoggerProvider.setProvider(logger);
        Scene scene = new Scene(bp, 350, 350);
        primaryStage.setTitle("Hello World!");
        primaryStage.setScene(scene);
        primaryStage.show();
        Button sendButton = new Button("Send msg");
//        sendButton.setOnAction(e -> {
//            try {
//                getClient().fakesend();
//            } catch (IOException ex) {
//                Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
//            } catch (InvalidKeyException ex) {
//                Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
//            }
//        });
        bp.setCenter(root);
        bp.setBottom(sendButton);
        startClientFlow();
    }
    
//    public Client getClient() {
//        if (this.client == null) {
//            client = new Client(this);
//        }
//        return client;
//    }

    public static SignalProtocolStoreImpl getSignalProtocolStore() {
        return signalProtocolStore;
    }
    
    private void startClientFlow() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    waveManager.startProvisioning(Elita.this);
//                    pm = new ProvisioningManager(waveManager, Elita.this);
  //                  pm.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                }
            }
        };
        t.start();
    }

    // callback from ProvisioningManager
    public void gotProvisionMessage(String number) {
        this.number = number;
        askLocalName();
    }

    public void askLocalName() {
        System.err.println("asklocalname");
        Label l = new Label("Enter the name you want to use for this device:");
        TextField tf = new TextField();
        Button btn = new Button("confirm");
        VBox vbox = new VBox(10, l, tf, btn);
        Platform.runLater(() -> {
            root.getChildren().clear();
            root.getChildren().add(vbox);
        });
        btn.setOnAction(b -> {
            final String name = tf.getText();
            try {
                btn.setDisable(true);
                Thread t = new Thread() {
                    @Override public void run() {
                        try {
                            waveManager.createAccount(number, name);
                        } catch (IOException ex) {
                            Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }; 
                t.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void gotProvisioningUrl(String url) {
        System.err.println("[Elita] setProvisioningURL to "+url);
        Image image = QRGenerator.getImage(url);
        ImageView iv = new ImageView(image);
        Platform.runLater(() -> root.getChildren().add(iv));
    }
    
    void saveIdentityKeyPair(byte[] b) throws InvalidKeyException {
        String dirname = System.getProperty("user.home")
                + File.separator + ".signalfx";
        File dir = new File(dirname);
        dir.mkdirs();
        Path path = dir.toPath().resolve("keypair");
        try {
            Files.write(path, b, StandardOpenOption.CREATE);
        } catch (IOException ex) {
            Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
        }
        storeIdentityKeyPair(b);
    }
    
    void storeIdentityKeyPair(byte[] b) throws InvalidKeyException {
        ECPrivateKey privateKey = Curve.decodePrivatePoint(b);
        ECPublicKey publicKey = Curve.createPublicKeyFromPrivateKey(b);
        ECKeyPair keyPair = new ECKeyPair(publicKey, privateKey);
        System.err.println("identitykp = "+ keyPair);
        IdentityKey identityKey = new IdentityKey(publicKey);
        IdentityKeyPair ikp = new IdentityKeyPair(identityKey, privateKey);
        getSignalProtocolStore().setIdentityKeyPair(ikp);
    }

    void retrieveIdentityKeyPair () throws InvalidKeyException {
                String dirname = System.getProperty("user.home")
                + File.separator + ".signalfx";
        File dir = new File(dirname);
        dir.mkdirs();
        Path path = dir.toPath().resolve("keypair");
        try {
            byte[] b = Files.readAllBytes(path);
            storeIdentityKeyPair(b);
        } catch (IOException ex) {
            Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void main(String[] args) throws Exception {
        try {
            Security.addProvider(new BouncyCastleProvider());
            Security.setProperty("crypto.policy", "unlimited");
            int maxKeySize = javax.crypto.Cipher.getMaxAllowedKeyLength("AES");
            System.err.println("max AES keysize = "+maxKeySize);
            launch();
         //   writeCred();
         //   readCred();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    static void writeCred() throws IOException {
     String dirname = System.getProperty("user.home")
                + File.separator+".signalfx";
     File dir = new File(dirname);
     dir.mkdirs();
        Path path = dir.toPath().resolve("credentials");
       File credFile = path.toFile();
       if (credFile.exists()) {
           credFile.delete();
       }
        java.nio.file.Files.writeString(path, "hello"+"\n", StandardOpenOption.CREATE);
           java.nio.file.Files.writeString(path, "world\n",  StandardOpenOption.APPEND);
           java.nio.file.Files.writeString(path, "here\n",  StandardOpenOption.APPEND);
           
     System.err.println("DONE writing");
    }
    
    static void readCred() throws IOException {
             String dirname = System.getProperty("user.home")
                + File.separator+".signalfx";
     File dir = new File(dirname);Path path = dir.toPath().resolve("credentials");
        List<String> lines = java.nio.file.Files.readAllLines(path);
        System.err.println("lines = "+lines);
    }
}
