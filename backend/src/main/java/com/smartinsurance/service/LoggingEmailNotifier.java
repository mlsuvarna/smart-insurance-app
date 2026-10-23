package com.smartinsurance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "log", matchIfMissing = true)
public class LoggingEmailNotifier implements EmailNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailNotifier.class);
    @Override
    public void notify(String recipient, String eventType) {
        LOG.info("Email delivery is not configured; notification event {} queued for masked recipient",
                eventType + " (" + mask(recipient) + ")");
    }
    private String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
