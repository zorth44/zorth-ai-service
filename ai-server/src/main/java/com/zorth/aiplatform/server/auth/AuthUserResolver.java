package com.zorth.aiplatform.server.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zorth.aiplatform.core.exception.AiClientException;
import com.zorth.aiplatform.server.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class AuthUserResolver {

    private final AuthContextClient client;
    private final Clock clock;
    private final long ttlSeconds;
    private final Cache<String, CachedUser> cache;

    public AuthUserResolver(AuthContextClient client, AuthProperties properties, Clock clock) {
        this.client = client;
        this.clock = clock;
        this.ttlSeconds = properties.cacheTtl().toSeconds();
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.cacheMaximumSize())
                .expireAfterWrite(Math.max(1, ttlSeconds), TimeUnit.SECONDS)
                .build();
    }

    public Optional<String> resolveOptional(String authorization) {
        Optional<String> token = bearerToken(authorization);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(resolveToken(token.get()));
        }
        catch (AiClientException exception) {
            return Optional.empty();
        }
    }

    public String requireUserId(String authorization) {
        String token = bearerToken(authorization).orElseThrow(AiClientException::unauthenticated);
        return resolveToken(token);
    }

    private String resolveToken(String token) {
        String digest = digest(token);
        if (ttlSeconds > 0) {
            CachedUser cached = cache.getIfPresent(digest);
            if (cached != null) {
                if (cached.expiresAt() == null || cached.expiresAt().isAfter(clock.instant())) {
                    return cached.userId();
                }
                cache.invalidate(digest);
                throw AiClientException.unauthenticated();
            }
        }
        String userId = client.resolveUserId(token);
        if (ttlSeconds > 0) {
            cache.put(digest, new CachedUser(userId, clock.instant().plusSeconds(ttlSeconds)));
        }
        return userId;
    }

    static Optional<String> bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            return Optional.empty();
        }
        String token = authorization.substring(7);
        if (!token.equals(token.trim()) || token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(token);
    }

    private static String digest(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        }
        catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CachedUser(String userId, java.time.Instant expiresAt) {
    }
}
