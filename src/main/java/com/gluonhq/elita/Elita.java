package com.gluonhq.elita;

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
            launch();
            //getAttachment();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Elita.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void getAttachment() throws Exception {
              SslContextFactory scf = new SslContextFactory(true);
        HttpClient httpClient = new HttpClient(scf);
        try {
            httpClient.start();
        } catch (Exception ex) {
            Logger.getLogger(SocketManager.class.getName()).log(Level.SEVERE, null, ex);
        }
//        String[] headerArr = new String[headers.size()];
//        headers.toArray(headerArr);
        ContentResponse res = httpClient.GET("https://cdn2.signal.org/attachments/OHmG9QNNYMiWPyDPEK9B");
        System.err.println("\n\n\n\n\n\n\n\nres = "+res);
//        String url ="https://cdn2.signal.org";
//String path = "attachments/OHmG9QNNYMiWPyDPEK9B";
//String method = "GET";
//System.err.println("create request to "+url);
//        Request request = httpClient.newRequest(url)
//                .method(method).path(path);
//                ContentResponse response = null;
//
//           try {
//            response = request.send();
//            System.err.println("got response: "+response);
//            System.err.println("RESP " + response.getContentAsString());
//            httpClient.stop();
//        } catch (Exception ex) {
//           ex.printStackTrace();
//        }
    }
}
