package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmPiiCipherTest {
    @Test
    void encryptsSensitiveValuesWithFreshIvAndCanDecryptThem() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        AesGcmPiiCipher cipher = new AesGcmPiiCipher(key);
        String first = cipher.encrypt("sensitive-id");
        String second = cipher.encrypt("sensitive-id");

        assertNotEquals(first, second);
        assertEquals("sensitive-id", cipher.decrypt(first));
    }
}
