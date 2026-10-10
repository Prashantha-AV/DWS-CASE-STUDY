package com.dws.challenge.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle invalid method arguments (MethodArgumentNotValidException) (400 - Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<List<String>> handleMethodArgumentValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        List<String> errorMessages = bindingResult.getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.toList());

        return new ResponseEntity<>(errorMessages, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle empty or malformed request body (HttpMessageNotReadableException) (400 - Bad Request)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleEmptyRequestBody(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>("Request body is empty or malformed", HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle ConstraintViolationException (Validation errors)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(ConstraintViolationException ex) {
        return buildErrorResponse("Validation failed: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     *  method to build a standard error response.
     */
    private ResponseEntity<Map<String, String>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", message);
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Handle InvalidAccountException (400 - Bad Request)
     */
    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAccountException(InvalidAccountException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle InsufficientFundsException (400 - Bad Request)
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientFundsException(InsufficientBalanceException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle ResourceLockException (409 - Conflict)
     */
    @ExceptionHandler(ResourceLockException.class)
    public ResponseEntity<Map<String, String>> handleLockException(ResourceLockException ex) {
        return buildErrorResponse("Resource conflict: " + ex.getMessage(), HttpStatus.CONFLICT);
    }

    /**
     * Handle other exceptions (500 - Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        return buildErrorResponse("Unexpected error: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
