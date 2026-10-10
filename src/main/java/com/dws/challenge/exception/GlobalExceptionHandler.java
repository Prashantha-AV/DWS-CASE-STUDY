package com.dws.challenge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Handles bean validation constraint failures (@NotNull, @DecimalMin, etc.) as HTTP 400 Bad Request. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    /** Handles missing, malformed, or unparseable JSON request bodies as HTTP 400 Bad Request (returns plain string). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMalformedJson(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>("Request body is empty or malformed", HttpStatus.BAD_REQUEST);
    }

    /** Handles domain business rule violations (insufficient balance, invalid amounts, duplicate accounts) as HTTP 400 Bad Request (returns plain string). */
    @ExceptionHandler({
            InsufficientBalanceException.class,
            InvalidTransactionAmountException.class,
            DuplicateAccountIdException.class
    })
    public ResponseEntity<String> handleBadRequestExceptions(RuntimeException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** Handles invalid or missing account IDs as HTTP 400 Bad Request (returns plain string). */
    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<String> handleAccountNotFound(InvalidAccountException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** Handles resource and concurrency locking conflicts as HTTP 409 Conflict. */
    @ExceptionHandler(ResourceLockException.class)
    public ResponseEntity<String> handleLockException(ResourceLockException ex) {
        return new ResponseEntity<>("Resource conflict: " + ex.getMessage(), HttpStatus.CONFLICT);
    }

    /** Catch-all fallback handler for unexpected runtime exceptions and internal system errors returning HTTP 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception ex) {
        return new ResponseEntity<>("Internal server error: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
