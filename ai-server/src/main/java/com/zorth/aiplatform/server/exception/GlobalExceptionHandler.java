package com.zorth.aiplatform.server.exception;

import com.zorth.aiplatform.core.exception.AiException;
import com.zorth.aiplatform.semantic.exception.SemanticBatchException;
import com.zorth.aiplatform.semantic.exception.SemanticGenerationAlreadyRunningException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        log.debug("Invalid AI request", exception);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "The request is invalid"));
    }

    @ExceptionHandler(SemanticGenerationAlreadyRunningException.class)
    ResponseEntity<ErrorResponse> handleSemanticAlreadyRunning(
            SemanticGenerationAlreadyRunningException exception) {
        log.info("Mapper semantic generation rejected because a batch is already running");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(SemanticBatchException.class)
    ResponseEntity<ErrorResponse> handleSemanticBatchException(SemanticBatchException exception) {
        log.warn("Mapper semantic batch failed code={}", exception.code());
        log.debug("Mapper semantic batch failed", exception);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(AiException.class)
    ResponseEntity<ErrorResponse> handleAiException(AiException exception) {
        log.error("AI service request failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("AI_SERVICE_ERROR", "The AI service is temporarily unavailable"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected server error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
