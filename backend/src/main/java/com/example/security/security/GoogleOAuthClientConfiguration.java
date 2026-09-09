package com.example.security.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/** Creates a Google client only when Google login is explicitly enabled. */
@Configuration
@ConditionalOnProperty(name = "app.oauth.google.enabled", havingValue = "true")
public class GoogleOAuthClientConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET are required when GOOGLE_OAUTH_ENABLED=true");
        }

        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId.trim())
                .clientSecret(clientSecret.trim())
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
