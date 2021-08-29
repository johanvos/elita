package com.gluonhq.elita;

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
            @Override public void run() {
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
            client.createAccount(provisionResult, name);
        });
    }
    
    public void setProvisioningURL(String url) {
        Image image = QRGenerator.getImage(url);
        ImageView iv = new ImageView(image);
        Platform.runLater(() -> root.getChildren().add(iv));
    }

}
