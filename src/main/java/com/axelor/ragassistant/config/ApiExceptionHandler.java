package com.axelor.ragassistant.config;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.axelor.ragassistant.chat.ChatGenerationException;
import com.axelor.ragassistant.document.DocumentProcessingCapacityException;
import com.axelor.ragassistant.exception.RequestValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiError> handleInvalidInput(RequestValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleUnexpectedIllegalArgument() {
        return ResponseEntity.badRequest().body(new ApiError("The request could not be processed."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaximumUploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("The file must not exceed 10 MiB."));
    }

    @ExceptionHandler(DocumentProcessingCapacityException.class)
    public ResponseEntity<ApiError> handleDocumentProcessingCapacity() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("Document processing is busy. Please try again shortly."));
    }

    @ExceptionHandler(ChatGenerationException.class)
    public ResponseEntity<ApiError> handleChatGenerationFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("The AI provider could not generate a response. Please try again."));
    }

    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class})
    public ResponseEntity<ApiError> handleModelFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("The AI provider is unavailable. Verify its configuration and try again."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseFailure() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("Document storage is unavailable. Verify the PostgreSQL and pgvector configuration."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "Document storage or retrieval is unavailable. Verify PostgreSQL, pgvector, and embedding configuration."));
    }

    public record ApiError(String message) {
    }
}
