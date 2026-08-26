package com.zorth.aiplatform.server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zorth.aiplatform.core.exception.AiClientException;
import com.zorth.aiplatform.server.config.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthUserResolverTest {

    @Mock
    private AuthContextClient client;

    private AuthUserResolver resolver;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                "http://127.0.0.1:8090/internal/api/v1/auth/context",
                "local-sql-editor-key",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                Duration.ofSeconds(60),
                10_000);
        resolver = new AuthUserResolver(
                client,
                properties,
                Clock.fixed(Instant.parse("2026-08-26T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void requireUserIdCachesByTokenDigest() {
        when(client.resolveUserId("raw-token")).thenReturn("1001");

        assertEquals("1001", resolver.requireUserId("Bearer raw-token"));
        assertEquals("1001", resolver.requireUserId("Bearer raw-token"));
        verify(client, times(1)).resolveUserId("raw-token");
    }

    @Test
    void missingAuthorizationIsUnauthenticatedForConversationApis() {
        AiClientException exception = assertThrows(AiClientException.class,
                () -> resolver.requireUserId(null));
        assertEquals(401, exception.status());
        assertEquals("UNAUTHENTICATED", exception.code());
        assertTrue(resolver.resolveOptional(null).isEmpty());
        assertTrue(resolver.resolveOptional("Bearer ").isEmpty());
    }

    @Test
    void authContextFailureIsOptionalEmptyOnAgentAndUnavailableWhenRequired() {
        when(client.resolveUserId("raw-token")).thenThrow(AiClientException.authUnavailable());

        assertEquals(Optional.empty(), resolver.resolveOptional("Bearer raw-token"));
        AiClientException exception = assertThrows(AiClientException.class,
                () -> resolver.requireUserId("Bearer raw-token"));
        assertEquals(503, exception.status());
        assertEquals("AUTH_SERVICE_UNAVAILABLE", exception.code());
    }
}
