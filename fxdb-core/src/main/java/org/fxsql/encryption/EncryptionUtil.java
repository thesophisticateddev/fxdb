package org.fxsql.encryption;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.fxsql.config.AppPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EncryptionUtil {
    private static final Logger logger = Logger.getLogger(EncryptionUtil.class.getName());
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 128;
    private static final int T_LEN = 128;
    private static final String KEY_DIRECTORY = AppPaths.getDir("META-DATA").getAbsolutePath();
    private static final String KEY_FILE = ".encryption.key";
    private static SecretKey secretKey;

    static {
        try {
            secretKey = loadOrGenerateKey();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize encryption key", e);
        }
    }

    private static SecretKey loadOrGenerateKey() throws Exception {
        File keyDir = new File(KEY_DIRECTORY);
        if (!keyDir.exists()) {
            keyDir.mkdirs();
        }

        File keyFile = new File(keyDir, KEY_FILE);

        if (keyFile.exists()) {
            // Load existing key
            try (FileInputStream fis = new FileInputStream(keyFile)) {
                byte[] keyBytes = fis.readAllBytes();
                logger.info("Encryption key loaded from file");
                return new SecretKeySpec(keyBytes, ALGORITHM);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load encryption key, generating new one", e);
            }
        }

        // Generate new key and save it
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE);
        SecretKey newKey = keyGen.generateKey();

        // Save the key to file
        try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            fos.write(newKey.getEncoded());
            logger.info("New encryption key generated and saved");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save encryption key", e);
        }

        return newKey;
    }

    public static String encrypt(String data) throws Exception {
        if (secretKey == null) {
            throw new IllegalStateException("Encryption key not initialized");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        byte[] encryptedDataWithIv = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, encryptedDataWithIv, 0, iv.length);
        System.arraycopy(encryptedData, 0, encryptedDataWithIv, iv.length, encryptedData.length);
        return Base64.getEncoder().encodeToString(encryptedDataWithIv);
    }

    public static String decrypt(String encryptedData) throws Exception {
        if (secretKey == null) {
            throw new IllegalStateException("Encryption key not initialized");
        }
        byte[] encryptedDataWithIv = Base64.getDecoder().decode(encryptedData);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(T_LEN, encryptedDataWithIv, 0, 12);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        byte[] decryptedData = cipher.doFinal(encryptedDataWithIv, 12, encryptedDataWithIv.length - 12);
        return new String(decryptedData);
    }
}
