package com.dws.challenge.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TransactionRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 2285597680730305298L;

    @NotNull(message = "Source account ID cannot be null.")
    @NotEmpty(message = "Source account ID cannot be empty.")
    private final String sourceAccountID;

    @NotNull(message = "Destination account ID cannot be null.")
    @NotEmpty(message = "Destination account ID cannot be empty.")
    private final String destAccountID;

    @NotNull(message = "Amount cannot be null.")
    @Positive(message = "Amount must be greater than zero.")
    private final BigDecimal amount;

    @JsonCreator
    public TransactionRequest(@JsonProperty("sourceAccountID") String sourceAccountID, @JsonProperty("destAccountID") String destAccountID, @JsonProperty("amount") BigDecimal amount) {
        this.sourceAccountID = sourceAccountID;
        this.destAccountID = destAccountID;
        this.amount = amount;
    }
}
