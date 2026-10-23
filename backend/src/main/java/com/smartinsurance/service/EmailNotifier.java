package com.smartinsurance.service;

public interface EmailNotifier {
    void notify(String recipient, String eventType);
}
