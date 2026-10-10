package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.exception.InvalidTransactionAmountException;
import com.dws.challenge.exception.ResourceLockException;
import com.dws.challenge.service.AccountsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
