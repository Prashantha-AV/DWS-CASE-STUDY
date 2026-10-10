package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.exception.InvalidAccountException;
import com.dws.challenge.exception.InvalidTransactionAmountException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountsServiceTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AccountsService accountsService;


    @Test
    void testCreateAccount() {
        Account account = new Account("1", BigDecimal.valueOf(100));
        accountsService.createAccount(account);
        verify(accountsRepository, times(1)).createAccount(account);
    }

    @Test
    void testGetAccount() {
        Account account = new Account("1", BigDecimal.valueOf(100));
        when(accountsRepository.getAccount("1")).thenReturn(account);
        Account result = accountsService.getAccount("1");
        assertEquals(account, result);
    }

    @Test
    void testGetAccount_ThrowsInvalidAccountException_WhenAccountNotFound() {
        when(accountsRepository.getAccount("1")).thenReturn(null);
        BigDecimal transferAmount = BigDecimal.valueOf(30);
        InvalidAccountException exception = assertThrows(InvalidAccountException.class, () -> {
            boolean transferResult = accountsService.transferFunds("1", "2", transferAmount);
        });

        assertEquals("Account not found for id 1", exception.getMessage());
    }

    @Test
    void testTransferFunds() throws Exception {
        Account sourceAccount = new Account("1", BigDecimal.valueOf(100));
        Account destinationAccount = new Account("2", BigDecimal.valueOf(50));
        BigDecimal transferAmount = BigDecimal.valueOf(30);

        when(accountsRepository.getAccount("1")).thenReturn(sourceAccount);
        when(accountsRepository.getAccount("2")).thenReturn(destinationAccount);

        doNothing().when(notificationService).notifyAboutTransfer(any(), any());
        boolean transferResult = accountsService.transferFunds("1", "2", transferAmount);
        assertTrue(transferResult);
        verify(notificationService, times(1)).notifyAboutTransfer(any(), any());
    }

    @Test
    void testTransferFunds_ThrowsInsufficientBalanceException_WhenSourceAccountHasInsufficientBalance() {
        Account sourceAccount = new Account("1", BigDecimal.valueOf(10)); // Insufficient balance
        Account destinationAccount = new Account("2", BigDecimal.valueOf(50));
        BigDecimal transferAmount = BigDecimal.valueOf(30);

        when(accountsRepository.getAccount("1")).thenReturn(sourceAccount);
        when(accountsRepository.getAccount("2")).thenReturn(destinationAccount);

        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class, () -> {
            accountsService.transferFunds("1", "2", transferAmount);
        });

        assertEquals("Insufficient funds: attempted to withdraw 30, but balance is 10", exception.getMessage());
    }

    @Test
    void testTransferFunds_ThrowsInvalidTransactionAmountException_WhenAmountIsZeroOrNegative() {
        BigDecimal transferAmount = BigDecimal.valueOf(0); // Invalid amount
        InvalidTransactionAmountException exception = assertThrows(InvalidTransactionAmountException.class, () -> {
            accountsService.transferFunds("1", "2", transferAmount);
        });

        assertEquals("Amount must be greater than zero.", exception.getMessage());
    }


    @Test
    void testProcessTransaction_ThrowsInsufficientBalanceException_WhenSourceAccountHasInsufficientBalance() {
        Account sourceAccount = new Account("1", BigDecimal.valueOf(10));
        Account destinationAccount = new Account("2", BigDecimal.valueOf(50));
        BigDecimal transferAmount = BigDecimal.valueOf(30);

        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class, () -> {
            accountsService.processTransaction(sourceAccount, destinationAccount, transferAmount);
        });

        assertEquals("Insufficient funds: attempted to withdraw 30, but balance is 10", exception.getMessage());
    }

    @Test
    public void testTransferAmountFromAcc1ToAcc2_thenCheckCorrectDebitAndCredit()  {

        Account sourceAccount = new Account("001", BigDecimal.valueOf(2000));
        Account destinationAccount = new Account("002", BigDecimal.valueOf(500));

        when(accountsRepository.getAccount("001")).thenReturn(sourceAccount);
        when(accountsRepository.getAccount("002")).thenReturn(destinationAccount);

        accountsService.processTransaction(sourceAccount, destinationAccount,
                new BigDecimal("500"));
        assertTrue(accountsService.getAccount(sourceAccount.getAccountId()).getBalance()
                .equals(new BigDecimal("1500")));
        assertTrue(accountsService.getAccount(destinationAccount.getAccountId()).getBalance()
                .equals(new BigDecimal("1000")));

    }


}
