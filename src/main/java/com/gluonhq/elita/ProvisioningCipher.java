package com.gluonhq.elita;
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
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.kdf.HKDFv3;
//import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.*;
import static signalservice.DeviceMessages.*;

public class ProvisioningCipher {
    
    final ECKeyPair ourKeyPair;

    public ProvisioningCipher() {
        ourKeyPair = Curve.generateKeyPair();
    }

    ProvisionDecryptResult decrypt(ProvisionEnvelope envelope) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, java.security.InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidProtocolBufferException {
        ProvisionDecryptResult answer = new ProvisionDecryptResult();
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
  
//            await verifyHmacSha256(
//      typedArrayToArrayBuffer(ivAndCiphertext),
//      keys[1],
//      typedArrayToArrayBuffer(mac),
//      32
//    );
//Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.toByteArray());
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[0], "AES"), ivSpec);
        byte[] doFinal = cipher.doFinal(cipherText.toByteArray());
        System.err.println("cipherText has "+doFinal.length);
        ProvisionMessage pm = ProvisionMessage.parseFrom(doFinal);
        System.err.println("NR = " + pm.getNumber());
        System.err.println("ct = " + new String(doFinal));
//    const plaintext = await decryptAes256CbcPkcsPadding(
//      keys[0],
//      typedArrayToArrayBuffer(ciphertext),
//      typedArrayToArrayBuffer(iv)
//    );
//    const provisionMessage = Proto.ProvisionMessage.decode(
//      new FIXMEU8(plaintext)
//    );
//    const privKey = provisionMessage.identityKeyPrivate;
//    strictAssert(privKey, 'Missing identityKeyPrivate in ProvisionMessage');
//
//    const keyPair = createKeyPair(typedArrayToArrayBuffer(privKey));
//
//    const { uuid } = provisionMessage;
//    strictAssert(uuid, 'Missing uuid in provisioning message');
//
//    const ret: ProvisionDecryptResult = {
//      identityKeyPair: keyPair,
//      number: provisionMessage.number,
//      uuid: normalizeUuid(uuid, 'ProvisionMessage.uuid'),
//      provisioningCode: provisionMessage.provisioningCode,
//      userAgent: provisionMessage.userAgent,
//      readReceipts: provisionMessage.readReceipts,
//    };
//    if (provisionMessage.profileKey) {
//      ret.profileKey = typedArrayToArrayBuffer(provisionMessage.profileKey);
//    }
//    return ret;
//
//        
        
        byte[] b = envelope.toByteArray();
        System.err.println("that's it");
        return answer;
    }
    
    
}
