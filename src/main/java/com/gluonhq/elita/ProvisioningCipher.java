package com.gluonhq.elita;
import com.gluonhq.elita.crypto.KeyUtil;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDFv3;
// import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.*;
import static signalservice.DeviceMessages.*;

public class ProvisioningCipher {
    
    final ECKeyPair ourKeyPair;

    public ProvisioningCipher() {
        ourKeyPair = Curve.generateKeyPair();
    }

    ProvisionMessage decrypt(ProvisionEnvelope envelope) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, java.security.InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidProtocolBufferException {
        ByteString masterEphemeral = envelope.getPublicKey();
        ECPublicKey ecPub = new ECPublicKey(masterEphemeral.toByteArray());
        ByteString message = envelope.getBody();
        if (message.byteAt(0) != 1 )  {
            throw new RuntimeException("First byte should be 1 in provisioningenvelope");
        }
        int mSize = message.size();
        System.err.println("msize = "+mSize);
        ByteString iv = message.substring(1, 17);
        ByteString mach = message.substring(mSize-32, mSize);
        ByteString versionAndivAndCipherText = message.substring(0, mSize -32);
        ByteString cipherText = message.substring(17, mSize -32);
        System.err.println("iv = "+iv);
        System.err.println("mac = "+ mach);
        System.err.println("ct = "+ cipherText);
        System.err.println("vivc = "+versionAndivAndCipherText);

        byte[] ecRes = Curve.calculateAgreement(ecPub, ourKeyPair.getPrivateKey());
        byte[] totkeys = new HKDFv3().deriveSecrets(ecRes, "TextSecure Provisioning Message".getBytes(), 64);
        byte[][] keys = new byte[2][32];
        System.arraycopy(totkeys, 0, keys[0], 0, 32);
        System.arraycopy(totkeys, 32, keys[1], 0, 32);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keys[1], "HmacSHA256"));
        byte[] calcMac = mac.doFinal(versionAndivAndCipherText.toByteArray());
        boolean macMatch = Arrays.equals(calcMac, mach.toByteArray());
        System.err.println("Mac match? "+macMatch);
  
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.toByteArray());
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[0], "AES"), ivSpec);
        byte[] doFinal = cipher.doFinal(cipherText.toByteArray());
        System.err.println("cipherText has "+doFinal.length);
        ProvisionMessage pm = ProvisionMessage.parseFrom(doFinal);
        System.err.println("NR = " + pm.getNumber());
        ECPrivateKey privateKey = Curve.decodePrivatePoint(pm.getIdentityKeyPrivate().toByteArray());
        ECPublicKey publicKey = privateKey.publicKey();
        ECKeyPair keyPair = new ECKeyPair(publicKey, privateKey);
        System.err.println("identitykp = "+ keyPair);
        KeyUtil.setIdentityKeyPair(keyPair);
        return pm;
    }
    
    
}
