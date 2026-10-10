package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.exception.InvalidTransactionAmountException;
import com.dws.challenge.exception.ResourceLockException;
import com.dws.challenge.service.AccountsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AccountsServiceConcurrentTest {

    public static final BigDecimal INITIAL_BALANCE_SOURCE = new BigDecimal("1000.00");
    public static final BigDecimal INITIAL_BALANCE_DESTINATION = new BigDecimal("500.00");

    @Autowired
    private AccountsService accountsService;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    public void setupAccounts() {
        // Initialize the source and destination accounts before each test run
        sourceAccount = new Account("Source", INITIAL_BALANCE_SOURCE);
        destinationAccount = new Account("Destination", INITIAL_BALANCE_DESTINATION);
    }

    @Test
    public void testConcurrentOneWayTransferProcessing() throws InterruptedException {
        Account sourceAccount = new Account("Source", new BigDecimal("1000.00"));
        Account destinationAccount = new Account("Destination", new BigDecimal("500.00"));
        accountsService.createAccount(sourceAccount);
        accountsService.createAccount(destinationAccount);

        final int numOfConcurrentThreads = 8;
        final BigDecimal transferAmount = new BigDecimal("100.00");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numOfConcurrentThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(numOfConcurrentThreads);

        for (int i = 0; i < numOfConcurrentThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    accountsService.transferFunds("Source", "Destination", transferAmount);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = completionLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(finished);

        BigDecimal expectedSourceBalance = new BigDecimal("1000.00")
                .subtract(transferAmount.multiply(new BigDecimal(numOfConcurrentThreads)));
        BigDecimal expectedDestinationBalance = new BigDecimal("500.00")
                .add(transferAmount.multiply(new BigDecimal(numOfConcurrentThreads)));

        assertEquals(expectedSourceBalance, accountsService.getAccount("Source").getBalance());
        assertEquals(expectedDestinationBalance, accountsService.getAccount("Destination").getBalance());
    }

    @Test
    public void testConcurrentBidirectionalTransfersDoNotDeadlock() throws InterruptedException {
        Account accountA = new Account("ACC-A", new BigDecimal("10000.00"));
        Account accountB = new Account("ACC-B", new BigDecimal("10000.00"));
        accountsService.createAccount(accountA);
        accountsService.createAccount(accountB);

        int totalThreads = 16;
        int transfersPerThread = 50;
        BigDecimal transferAmount = new BigDecimal("10.00");

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalThreads);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final boolean isEven = (i % 2 == 0);
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < transfersPerThread; j++) {
                        if (isEven) {
                            accountsService.transferFunds("ACC-A", "ACC-B", transferAmount);
                        } else {
                            accountsService.transferFunds("ACC-B", "ACC-A", transferAmount);
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed);
        assertEquals(0, errorCount.get());

        BigDecimal balanceA = accountsService.getAccount("ACC-A").getBalance();
        BigDecimal balanceB = accountsService.getAccount("ACC-B").getBalance();

        assertEquals(0, balanceA.add(balanceB).compareTo(new BigDecimal("20000.00")));
    }

    @Test
    void testBiDirectionalTransfersDoNotDeadlock() throws InterruptedException {
        Account accountA = new Account("Account-A", new BigDecimal("10000.00"));
        Account accountB = new Account("Account-B", new BigDecimal("10000.00"));

        accountsService.createAccount(accountA);
        accountsService.createAccount(accountB);

        int numberOfThreads = 20;
        int transfersPerThread = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            final boolean isEven = (i % 2 == 0);
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < transfersPerThread; j++) {
                        if (isEven) {
                            accountsService.transferFunds("Account-A", "Account-B", new BigDecimal("10.00"));
                        } else {
                            accountsService.transferFunds("Account-B", "Account-A", new BigDecimal("10.00"));
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed);
        BigDecimal totalBalance = accountsService.getAccount("Account-A").getBalance()
                .add(accountsService.getAccount("Account-B").getBalance());

        assertEquals(0, totalBalance.compareTo(new BigDecimal("20000.00")));
    }

    @RepeatedTest(3)
    public void testConcurrentTransferProcessing() throws InterruptedException {
        final int numOfConcurrentThreads = 8;
        final BigDecimal transferAmount = new BigDecimal("100.00");

        // Use CountDownLatch to ensure all threads complete their transfers before performing assertions
        CountDownLatch completionLatch = new CountDownLatch(numOfConcurrentThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(numOfConcurrentThreads);

        // Submit multiple transfer tasks to the executor
        IntStream.range(0, numOfConcurrentThreads).forEach(threadIndex -> executorService.submit(() -> {
            try {
                // Perform the transfer operation
                accountsService.processTransaction(sourceAccount, destinationAccount, transferAmount);
            } catch (InsufficientBalanceException | InvalidTransactionAmountException | ResourceLockException e) {
                fail("Transfer operation failed due to: " + e.getMessage());
            } finally {
                // Decrement the latch count when a thread finishes processing
                completionLatch.countDown();
            }
        }));

        // Wait for all transfer tasks to complete
        completionLatch.await();
        executorService.shutdown();

        // Calculate the expected final balances after all transfers
        BigDecimal expectedSourceBalance = INITIAL_BALANCE_SOURCE.subtract(transferAmount.multiply(new BigDecimal(numOfConcurrentThreads)));
        BigDecimal expectedDestinationBalance = INITIAL_BALANCE_DESTINATION.add(transferAmount.multiply(new BigDecimal(numOfConcurrentThreads)));

        // Verify that the sourceAccount balance was decreased correctly
        assertEquals(expectedSourceBalance, sourceAccount.getBalance(), "Source account's balance is incorrect after transfers.");

        // Verify that the destinationAccount balance was increased correctly
        assertEquals(expectedDestinationBalance, destinationAccount.getBalance(), "Destination account's balance is incorrect after transfers.");
    }

}
