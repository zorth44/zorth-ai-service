package com.zorth.aiplatform.server.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthContextPayload(String userId, Instant tokenExpiresAt) {
}
