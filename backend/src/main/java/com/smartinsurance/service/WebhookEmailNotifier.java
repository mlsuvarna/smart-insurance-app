package com.smartinsurance.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.email.mode", havingValue = "webhook")
public class WebhookEmailNotifier implements EmailNotifier {
    private final RestClient client;
    private final String endpoint;
    private final String token;
    public WebhookEmailNotifier(RestClient.Builder builder,
                                @Value("${EMAIL_WEBHOOK_URL:}") String endpoint,
                                @Value("${EMAIL_API_TOKEN:}") String token) {
        if (endpoint.isBlank() || token.isBlank()) {
            throw new IllegalStateException("EMAIL_WEBHOOK_URL and EMAIL_API_TOKEN are required when EMAIL_MODE=webhook");
        }
        this.client = builder.build();
        this.endpoint = endpoint;
        this.token = token;
    }
    @Override
    public void notify(String recipient, String eventType) {
        client.post().uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("recipient", recipient, "eventType", eventType))
                .retrieve().toBodilessEntity();
    }
}
