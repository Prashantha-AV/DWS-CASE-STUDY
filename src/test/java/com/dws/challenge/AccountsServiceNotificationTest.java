package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.exception.InvalidAccountException;
import com.dws.challenge.exception.InvalidTransactionAmountException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsServiceNotificationTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AccountsService accountsService;

    @BeforeEach
    void setUp() {
        reset(accountsRepository, notificationService);
    }

    @ParameterizedTest(name = "Should reject non-positive amount: {0} and trigger 0 notifications")
    @ValueSource(strings = {"0", "-1", "-100.50", "-0.01"})
    void transferFunds_ShouldFailAndNotNotify_WhenAmountIsZeroOrNegative(String invalidAmountStr) {
        BigDecimal invalidAmount = new BigDecimal(invalidAmountStr);

        assertThatThrownBy(() -> accountsService.transferFunds("Acc-1", "Acc-2", invalidAmount))
                .isInstanceOf(InvalidTransactionAmountException.class)
                .hasMessageContaining("Amount must be greater than zero.");

        verifyNoInteractions(accountsRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void transferFunds_ShouldFailAndNotNotify_WhenAmountIsNull() {
        assertThatThrownBy(() -> accountsService.transferFunds("Acc-1", "Acc-2", null))
                .isInstanceOf(InvalidTransactionAmountException.class)
                .hasMessageContaining("Amount must be greater than zero.");

        verifyNoInteractions(accountsRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void transferFunds_ShouldFailAndNotNotify_WhenSelfTransferAttempted() {
        assertThatThrownBy(() -> accountsService.transferFunds("Acc-Same", "Acc-Same", new BigDecimal("50.00")))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("Source and destination accounts must be different.");

        verifyNoInteractions(accountsRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void transferFunds_ShouldFailAndNotNotify_WhenInsufficientFunds() {
        Account sender = new Account("Sender-Low", new BigDecimal("49.99"));
        Account receiver = new Account("Receiver-Normal", new BigDecimal("100.00"));

        when(accountsRepository.getAccount("Sender-Low")).thenReturn(sender);
        when(accountsRepository.getAccount("Receiver-Normal")).thenReturn(receiver);

        assertThatThrownBy(() -> accountsService.transferFunds("Sender-Low", "Receiver-Normal", new BigDecimal("50.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(receiver.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        verifyNoInteractions(notificationService);
    }

    @Test
    void transferFunds_ShouldFailAndNotNotify_WhenSourceAccountNotFound() {
        when(accountsRepository.getAccount("Missing-Source")).thenReturn(null);

        assertThatThrownBy(() -> accountsService.transferFunds("Missing-Source", "Dest-Valid", new BigDecimal("50.00")))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("Account not found for id Missing-Source");

        verifyNoInteractions(notificationService);
    }

    @Test
    void transferFunds_ShouldFailAndNotNotify_WhenDestinationAccountNotFound() {
        Account sender = new Account("Sender-Valid", new BigDecimal("1000.00"));
        when(accountsRepository.getAccount("Sender-Valid")).thenReturn(sender);
        when(accountsRepository.getAccount("Missing-Dest")).thenReturn(null);

        assertThatThrownBy(() -> accountsService.transferFunds("Sender-Valid", "Missing-Dest", new BigDecimal("50.00")))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("Account not found for id Missing-Dest");

        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        verifyNoInteractions(notificationService);
    }
}