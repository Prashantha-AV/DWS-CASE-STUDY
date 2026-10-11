package com.dws.challenge.web;

import com.dws.challenge.domain.Account;
import com.dws.challenge.dto.TransactionRequest;
import com.dws.challenge.exception.DuplicateAccountIdException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
            tags = {"Account"}
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
    public ResponseEntity<Account> getAccount(@PathVariable String accountId) {
        log.info("Fetching account details for account ID: {}", accountId);

        Account account = this.accountsService.getAccount(accountId);
        return ResponseEntity.ok(account);
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

        log.info("Initiating transfer of {} from {} to {}",
                transactionRequest.getAmount(),
                transactionRequest.getSourceAccountID(),
                transactionRequest.getDestAccountID());

        accountsService.transferFunds(
                transactionRequest.getSourceAccountID(),
                transactionRequest.getDestAccountID(),
                transactionRequest.getAmount()
        );

        log.info("Transfer of {} completed successfully from account {} to account {}",
                transactionRequest.getAmount(), transactionRequest.getSourceAccountID(), transactionRequest.getDestAccountID());
        //response error code 400, 500 handled in GlobalExceptionHandler
        return ResponseEntity.ok().build();
    }
}
