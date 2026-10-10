package com.dws.challenge.exception;

public class InvalidTransactionAmountException extends RuntimeException {

    public InvalidTransactionAmountException(String message) {
        super(message);
    }
}
