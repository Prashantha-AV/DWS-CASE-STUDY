package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

@SpringBootTest
public class AccountsControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private AccountsService accountsService;

    @BeforeEach
    void prepareMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .build();
        accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    void createAccountTest() throws Exception {
        String uniqueAccountId = "001";
        BigDecimal initialBalance = new BigDecimal("1500.00");
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isCreated());

        Account account = accountsService.getAccount(uniqueAccountId);
        assertThat(account.getAccountId()).isEqualTo(uniqueAccountId);
        assertThat(account.getBalance()).isEqualByComparingTo(initialBalance);
    }

    @Test
    void createDuplicateAccountTest() throws Exception {
        String duplicateAccountId = "001";
        BigDecimal initialBalance = new BigDecimal("3000.00");

        // Create first account
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + duplicateAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isCreated());

        // Try to create the same account again
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + duplicateAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account id " + duplicateAccountId + " already exists!"));
    }

    @Test
    void createAccountNoAccountIdTest() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"balance\":2500}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAccountNoBalanceTest() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"001\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAccountNegativeBalanceTest() throws Exception {
        String accountId = "Account-1122";
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + accountId + "\",\"balance\":-500}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAccountTest() throws Exception {
        String accountId = "Account-" + System.currentTimeMillis();
        Account account = new Account(accountId, new BigDecimal("1000.50"));
        this.accountsService.createAccount(account);

        this.mockMvc.perform(get("/v1/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"accountId\":\"" + accountId + "\",\"balance\":1000.50}"));
    }

    @Test
    public void transferAmountWithNoBody_BadRequestTest() throws Exception {
        this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Request body is empty or malformed"));
    }

    @Test
    public void transferAmountFromNonExistingAccount_BadRequestTest() throws Exception {
        this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"Account-9999\",\"toAccountId\":\"Account-8888\",\"amount\":500}"))
                .andExpect(status().isBadRequest());
    }


    @Test
    public void transferAmountFromAccountToToAccount_SUCCESSTest() throws Exception {
        String srcAccountId = "001";
        String destAccountId = "002";
        BigDecimal initialBalance = new BigDecimal("2000.00");
        BigDecimal transferAmount = new BigDecimal("1000.00");

        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + srcAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isCreated());

        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + destAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isCreated());

        this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountID\":\"" + srcAccountId + "\",\"destAccountID\":\"" + destAccountId + "\",\"amount\":" + transferAmount + "}"))
                .andExpect(status().isOk());

        assertThat(accountsService.getAccount(srcAccountId).getBalance()).isEqualByComparingTo("1000.00");
        assertThat(accountsService.getAccount(destAccountId).getBalance()).isEqualByComparingTo("3000.00");
    }

    @Test
    public void transferAmountGreaterThanBalance_BadRequestTest() throws Exception {
        String fromAccountId = "Account-5000";
        String toAccountId = "Account-6000";
        BigDecimal initialBalance = new BigDecimal("1500.00");

        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + fromAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isCreated());

        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + toAccountId + "\",\"balance\":" + initialBalance + "}"))
                .andExpect(status().isCreated());

        this.mockMvc.perform(post("/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountID\":\"" + fromAccountId + "\",\"destAccountID\":\"" + toAccountId + "\",\"amount\":2000}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Insufficient funds: attempted to withdraw 2000, but balance is 1500.00"));
    }
}
