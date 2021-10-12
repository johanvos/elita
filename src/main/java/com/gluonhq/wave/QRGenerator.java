package com.gluonhq.wave;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class QRGenerator {

    static final int WIDTH = 300;
    static final int HEIGHT = 300;

    public static Image getImage(String url) {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        WritableImage answer = null;
        try {
            BitMatrix byteMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, WIDTH, HEIGHT);
            answer = new WritableImage(WIDTH, HEIGHT);
            PixelWriter writer = answer.getPixelWriter();
            for (int i = 0; i < WIDTH; i++) {
                for (int j = 0; j < HEIGHT; j++) {
                    Color c = Color.WHITE;
                    if (byteMatrix.get(i, j)) {
                        c = Color.BLACK;
                    }
                    writer.setColor(i, j, c);
                }
            }
        } catch (WriterException ex) {
            ex.printStackTrace();
        }
        return answer;
    }
}
