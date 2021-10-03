package com.gluonhq.wave;

// taken from http://java-buddy.blogspot.com/2015/01/qrcode-generator-on-javafx-using-zxing.html

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 *
 * @web http://java-buddy.blogspot.com/
 */
public class QRGenerator { //extends Application {
    
    private ImageView qrView = new ImageView();
 //   private Client client = new Client();
//    
//    @Override
//    public void start(Stage primaryStage) {
//       
//        StackPane root = new StackPane();
//        root.getChildren().add(qrView);
//        
//        Scene scene = new Scene(root, 350, 350);
//        
//        primaryStage.setTitle("Hello World!");
//        primaryStage.setScene(scene);
//        primaryStage.show();
//        Thread t = new Thread() {
//            @Override public void run() {
//                client.startup(QRGenerator.this);
//            }
//        };
//        t.start();
//    }
//
//    public void setURL(String url) {
//        Image image = getImage(url);
//        Platform.runLater(() -> qrView.setImage(image));
//    }
//    
    public static Image getImage(String url) {     
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        int width = 300;
        int height = 300;
        String fileType = "png";
        
        BufferedImage bufferedImage = null;
        try {
            Hashtable hints = new Hashtable();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
            BitMatrix byteMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, width, height, hints);
            bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            bufferedImage.createGraphics();
            
            Graphics2D graphics = (Graphics2D) bufferedImage.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (byteMatrix.get(i, j)) {
                        graphics.fillRect(i, j, 1, 1);
                    }
                }
            }
                        
        } catch (WriterException ex) {
            ex.printStackTrace();
        }
        return SwingFXUtils.toFXImage(bufferedImage, null);
    }
    
//    public static void main(String[] args) {
//        launch(args);
//    }
    
}