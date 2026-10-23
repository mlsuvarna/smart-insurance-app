package com.smartinsurance.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesGcmPiiCipher implements PiiCipher {
    private static final int IV_BYTES = 12;
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();
    public AesGcmPiiCipher(@Value("${PII_ENCRYPTION_KEY:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }
    @Override
    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt protected customer data", ex);
        }
    }
    @Override
    public String decrypt(String value) {
        if (value == null) return null;
        try {
            byte[] stored = Base64.getDecoder().decode(value);
            if (stored.length <= IV_BYTES) throw new IllegalArgumentException("Invalid encrypted data");
            ByteBuffer buffer = ByteBuffer.wrap(stored);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt protected customer data", ex);
        }
    }
    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) throw new IllegalStateException("PII_ENCRYPTION_KEY must be base64-encoded 32-byte key");
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY must be base64-encoded 32-byte key", ex);
        }
    }
}
