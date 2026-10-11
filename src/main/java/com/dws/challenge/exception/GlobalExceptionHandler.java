package com.dws.challenge.exception;

import com.dws.challenge.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Handles bean validation constraint failures (@NotNull, @DecimalMin, etc.) as HTTP 400 Bad Request. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                validationErrors
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /** Handles missing, malformed, or unparseable JSON request bodies as HTTP 400 Bad Request (returns plain string). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Request body is empty or malformed"
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /** Handles domain business rule violations (insufficient balance, invalid amounts, duplicate accounts) as HTTP 400 Bad Request (returns plain string). */
    @ExceptionHandler({
            InsufficientBalanceException.class,
            InvalidTransactionAmountException.class,
            DuplicateAccountIdException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(RuntimeException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /** Handles invalid or missing account IDs as HTTP 400 Bad Request (returns plain string). */
    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(InvalidAccountException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /** Handles resource and concurrency locking conflicts as HTTP 409 Conflict. */
    @ExceptionHandler(ResourceLockException.class)
    public ResponseEntity<ErrorResponse> handleLockException(ResourceLockException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "Resource conflict: " + ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /** Catch-all fallback handler for unexpected runtime exceptions and internal system errors returning HTTP 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Internal server error: " + ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}