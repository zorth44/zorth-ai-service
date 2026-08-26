package com.zorth.aiplatform.server.auth;

import com.zorth.aiplatform.core.exception.AiClientException;
import com.zorth.aiplatform.server.config.AuthProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class AuthContextClient {

    static final String INTERNAL_SERVICE_KEY_HEADER = "X-Internal-Service-Key";

    private final RestClient restClient;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthContextClient(RestClient restClient, AuthProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    public String resolveUserId(String token) {
        try {
            AuthContextPayload payload = restClient.get()
                    .uri(properties.contextUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(INTERNAL_SERVICE_KEY_HEADER, properties.internalServiceKey())
                    .retrieve()
                    .body(AuthContextPayload.class);
            return requireValidUserId(payload);
        }
        catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw AiClientException.unauthenticated();
            }
            throw AiClientException.authUnavailable();
        }
        catch (RestClientException exception) {
            throw AiClientException.authUnavailable();
        }
    }

    private String requireValidUserId(AuthContextPayload payload) {
        if (payload == null || payload.userId() == null || payload.userId().isBlank()) {
            throw AiClientException.unauthenticated();
        }
        if (payload.tokenExpiresAt() != null && !payload.tokenExpiresAt().isAfter(clock.instant())) {
            throw AiClientException.unauthenticated();
        }
        return payload.userId().trim();
    }
}
