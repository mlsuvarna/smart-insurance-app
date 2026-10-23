package com.smartinsurance.service;

public interface PiiCipher {
    String encrypt(String value);
    String decrypt(String value);
}
