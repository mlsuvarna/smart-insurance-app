package com.smartinsurance.service;

public interface DocumentStorage {
    void store(String key, byte[] content);
    byte[] load(String key);
    void delete(String key);
}
