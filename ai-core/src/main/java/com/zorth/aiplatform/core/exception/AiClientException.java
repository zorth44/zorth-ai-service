package com.zorth.aiplatform.core.exception;

public class AiClientException extends RuntimeException {

    private final int status;
    private final String code;

    public AiClientException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static AiClientException unauthenticated() {
        return new AiClientException(401, "UNAUTHENTICATED", "The request is unauthenticated");
    }

    public static AiClientException authUnavailable() {
        return new AiClientException(503, "AUTH_SERVICE_UNAVAILABLE",
                "The authorization service is temporarily unavailable");
    }

    public static AiClientException conversationNotFound() {
        return new AiClientException(404, "CONVERSATION_NOT_FOUND", "The conversation was not found");
    }
}
