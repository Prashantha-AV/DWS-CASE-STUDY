package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.repository.AccountsRepository;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsServiceNotificationConcurrentTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AccountsService accountsService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @BeforeEach
    void setUp() {
        reset(accountsRepository, notificationService);
    }

    @Test
    @DisplayName("High Concurrency: Exactly 2 notifications per transaction under high load")
    void concurrentTransfers_ShouldNotifyBothAccountsForEachSuccessfulTransfer() throws InterruptedException {
        int threadCount = 20;
        Account fromAccount = new Account("Acc-Sender", new BigDecimal("10000"));
        Account toAccount = new Account("Acc-Receiver", BigDecimal.ZERO);

        when(accountsRepository.getAccount("Acc-Sender")).thenReturn(fromAccount);
        when(accountsRepository.getAccount("Acc-Receiver")).thenReturn(toAccount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    accountsService.transferFunds("Acc-Sender", "Acc-Receiver", new BigDecimal("10"));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        verify(notificationService, times(threadCount * 2))
                .notifyAboutTransfer(any(Account.class), anyString());
        verify(notificationService, times(threadCount))
                .notifyAboutTransfer(eq(fromAccount), eq("Transferred 10 to account Acc-Receiver"));
        verify(notificationService, times(threadCount))
                .notifyAboutTransfer(eq(toAccount), eq("Received 10 from account Acc-Sender"));
    }

    @Test
    @DisplayName("Race Condition & Competing Debits: Zero notifications for failed overdraft attempts")
    void concurrentCompetingTransfers_ShouldOnlyNotifyTheWinningTransaction() throws InterruptedException {
        int threadCount = 10;
        Account fromAccount = new Account("Sender-100", new BigDecimal("100"));
        Account toAccount = new Account("Receiver-100", BigDecimal.ZERO);

        when(accountsRepository.getAccount("Sender-100")).thenReturn(fromAccount);
        when(accountsRepository.getAccount("Receiver-100")).thenReturn(toAccount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    boolean result = accountsService.transferFunds("Sender-100", "Receiver-100", new BigDecimal("100"));
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        verify(notificationService, times(2))
                .notifyAboutTransfer(any(Account.class), anyString());
        verify(notificationService, times(1))
                .notifyAboutTransfer(eq(fromAccount), eq("Transferred 100 to account Receiver-100"));
        verify(notificationService, times(1))
                .notifyAboutTransfer(eq(toAccount), eq("Received 100 from account Sender-100"));
    }

    @Test
    @DisplayName("Bidirectional Contention (A <-> B): Deadlock-free with accurate counterparty notifications")
    void concurrentBidirectionalTransfers_DeadlockFreeWithCounterpartyMessageIntegrity() throws Exception {
        int pairsCount = 10;
        Account accountA = new Account("ACC-A", new BigDecimal("5000"));
        Account accountB = new Account("ACC-B", new BigDecimal("5000"));

        when(accountsRepository.getAccount("ACC-A")).thenReturn(accountA);
        when(accountsRepository.getAccount("ACC-B")).thenReturn(accountB);

        ExecutorService executor = Executors.newFixedThreadPool(pairsCount * 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < pairsCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                accountsService.transferFunds("ACC-A", "ACC-B", new BigDecimal("25"));
                return null;
            }));
            futures.add(executor.submit(() -> {
                startLatch.await();
                accountsService.transferFunds("ACC-B", "ACC-A", new BigDecimal("25"));
                return null;
            }));
        }

        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        int totalTransactions = pairsCount * 2;
        verify(notificationService, times(totalTransactions * 2))
                .notifyAboutTransfer(accountCaptor.capture(), messageCaptor.capture());

        List<String> messages = messageCaptor.getAllValues();
        assertThat(messages).hasSize(40);

        long aToBMessages = messages.stream()
                .filter(m -> m.equals("Transferred 25 to account ACC-B") || m.equals("Received 25 from account ACC-A"))
                .count();
        long bToAMessages = messages.stream()
                .filter(m -> m.equals("Transferred 25 to account ACC-A") || m.equals("Received 25 from account ACC-B"))
                .count();

        assertThat(aToBMessages).isEqualTo(20);
        assertThat(bToAMessages).isEqualTo(20);
    }
}