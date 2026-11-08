/*==========================================================================
Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
============================================================================*/
package com.example.monitor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

final class TwilioSmsClient {
    private final MonitorConfig config;
    private final HttpClient client;
    private final Map<String, Instant> lastSent = new HashMap<>();

    TwilioSmsClient(MonitorConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    synchronized boolean send(String alertKey, String message, boolean bypassCooldown) throws Exception {
        if (!config.smsEnabled()) return false;
        Instant previous = lastSent.get(alertKey);
        if (!bypassCooldown && previous != null
                && previous.plus(config.smsCooldown()).isAfter(Instant.now())) {
            return false;
        }

        String form = "To=" + encode(config.smsTo())
                + "&From=" + encode(config.twilioFrom())
                + "&Body=" + encode(message);
        String credentials = config.twilioAccountSid() + ":" + config.twilioAuthToken();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                + config.twilioAccountSid() + "/Messages.json");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio returned HTTP " + response.statusCode());
        }
        lastSent.put(alertKey, Instant.now());
        return true;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
