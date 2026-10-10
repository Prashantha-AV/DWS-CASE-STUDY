package com.dws.challenge.web;

import com.dws.challenge.domain.Account;
import com.dws.challenge.dto.TransactionRequest;
import com.dws.challenge.exception.*;
import com.dws.challenge.service.AccountsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/v1/accounts")
@Slf4j
public class AccountsController {

    private final AccountsService accountsService;

    @Autowired
    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createAccount(@RequestBody @Valid Account account) {
        log.info("Creating account {}", account);

        try {
            this.accountsService.createAccount(account);
        } catch (DuplicateAccountIdException daie) {
            return new ResponseEntity<>(daie.getMessage(), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * Retrieves the account details by account ID.
     *
     * @param accountId The account ID.
     * @return The account details if found.
     */
    @Operation(
            summary = "Retrieve account details by account ID",
            description = "This endpoint retrieves the details of an account based on the provided account ID.",
            tags = {"Account" }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Account retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Account.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping(path = "/{accountId}")
    public Account getAccount(@PathVariable String accountId) {
        log.info("Fetching account details for account ID: {}", accountId);
        return accountsService.getAccount(accountId);
    }

    /**
     * Transfers an amount between two accounts.
     *
     * @param transactionRequest The request containing the source and destination account details and transfer amount.
     * @return ResponseEntity with the status of the transfer.
     */
    @Operation(
            summary = "Transfer amount between two accounts",
            description = "This endpoint allows transferring an amount from one account to another.",
            tags = {"Transfer" }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Amount transferred successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping(path = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> transferAmount(@RequestBody @Valid TransactionRequest transactionRequest) {

        try {
            accountsService.transferFunds(
                    transactionRequest.getSourceAccountID(),
                    transactionRequest.getDestAccountID(),
                    transactionRequest.getAmount()
            );
        } catch (InsufficientBalanceException exception) {
            log.error("Transfer failed due to insufficient funds: {}", exception.getMessage());
            return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (InvalidTransactionAmountException exception) {
            log.error("Transfer failed due to invalid amount: {}", exception.getMessage());
            return new ResponseEntity<>(exception.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ResourceLockException exception) {
            log.error("Transfer failed due to resource lock issues: {}", exception.getMessage());
            return new ResponseEntity<>(exception.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Transfer of {} completed successfully from account {} to account {}",
                transactionRequest.getAmount(), transactionRequest.getSourceAccountID(), transactionRequest.getDestAccountID());
        return ResponseEntity.ok().build();
    }
}
