package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.exception.InsufficientBalanceException;
import com.dws.challenge.exception.InvalidAccountException;
import com.dws.challenge.exception.InvalidTransactionAmountException;
import com.dws.challenge.exception.ResourceLockException;
import com.dws.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import static com.dws.challenge.utils.TransactionLogging.logTransferDetails;

@Service
@Getter
@Slf4j
public class AccountsService {

  private final AccountsRepository accountsRepository;
  private final NotificationService notificationService;

  @Autowired
  public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
    this.accountsRepository = accountsRepository;
      this.notificationService = notificationService;
  }

  /**
   * Creates a new account and saves it to the repository.
   *
   * @param account The account to be created
   */
  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  /**
   * Retrieves an account by its ID from the repository.
   *
   * @param accountId The ID of the account to retrieve
   * @return The account associated with the given accountId
   */
  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  /**
   * Transfers funds from the source account to the destination account.
   * Validates the transfer amount, locks the accounts, performs the transfer, and sends a notification.
   *
   * @param sourceAccountId The account ID from which funds are withdrawn
   * @param destinationAccountId The account ID to which funds are deposited
   * @param amountToTransfer The amount to transfer
   * @return true if the transfer was successful, false otherwise
   * @throws InsufficientBalanceException If there are not enough funds in the source account
   * @throws InvalidAccountException If any of the provided account IDs are invalid
   * @throws InvalidTransactionAmountException If the transfer amount is invalid (e.g., non-positive)
   * @throws ResourceLockException If there is an issue acquiring locks on the accounts
   */
  public boolean transferFunds(
          final String sourceAccountId,
          final String destinationAccountId,
          final BigDecimal amountToTransfer
  ) throws InsufficientBalanceException, InvalidAccountException, InvalidTransactionAmountException, ResourceLockException {

    validateTransferAmount(amountToTransfer);

    if (sourceAccountId.equals(destinationAccountId)) {
        throw new InvalidAccountException("Source and destination accounts must be different.");
    }

    Account sourceAccount = retrieveAccountOrThrow(sourceAccountId);
    Account destinationAccount = retrieveAccountOrThrow(destinationAccountId);

    boolean transferSuccess = processTransaction(sourceAccount, destinationAccount, amountToTransfer);
    if (transferSuccess) {
      notifyTransferCompletion(sourceAccount, destinationAccount, amountToTransfer);
      logTransferDetails(amountToTransfer, sourceAccount.getAccountId(),
              destinationAccount.getAccountId(), sourceAccount.getBalance(),
              destinationAccount.getBalance());
    }
    return transferSuccess;
  }

  /**
   * Processes a transfer transaction between two accounts.
   * This method validates the transfer amount, locks both accounts to prevent concurrency issues,
   * performs the transaction (withdrawal from source account and deposit to destination account),
   * and handles resource locking in strict natural order to eliminate deadlocks.
   *
   * @param sourceAccount The account from which funds are withdrawn
   * @param destinationAccount The account to which funds are deposited
   * @param transferAmount The amount to be transferred
   * @return true if the transaction is successfully completed
   * @throws InsufficientBalanceException If the source account has insufficient balance
   * @throws InvalidTransactionAmountException If the transfer amount is invalid (zero or negative)
   * @throws ResourceLockException If the resource locks cannot be acquired or thread is interrupted
   */
  public boolean processTransaction(final Account sourceAccount, final Account destinationAccount, final BigDecimal transferAmount)
          throws InsufficientBalanceException, InvalidTransactionAmountException, ResourceLockException {

    validateTransferAmount(transferAmount);

    // Natural ordering without array allocation or sort overhead
    boolean isSourceFirst = sourceAccount.getAccountId().compareTo(destinationAccount.getAccountId()) < 0;
    Account firstLock = isSourceFirst ? sourceAccount : destinationAccount;
    Account secondLock = isSourceFirst ? destinationAccount : sourceAccount;

    boolean firstLockAcquired = false;
    boolean secondLockAcquired = false;

    try {
      firstLockAcquired = attemptToLockAccount(firstLock);
      secondLockAcquired = attemptToLockAccount(secondLock);

      if (firstLockAcquired && secondLockAcquired) {
        performTransaction(sourceAccount, destinationAccount, transferAmount);
      } else {
        throw new ResourceLockException("Failed to acquire locks on both accounts.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResourceLockException("Thread interrupted while acquiring locks.");
    } finally {
      releaseLockIfHeld(secondLock, secondLockAcquired);
      releaseLockIfHeld(firstLock, firstLockAcquired);
    }

    return true;
  }

  /**
   * Validates that the transfer amount is positive.
   *
   * @param amount The amount to transfer
   * @throws InvalidTransactionAmountException If the amount is zero or negative
   */
  private void validateTransferAmount(BigDecimal amount) throws InvalidTransactionAmountException {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidTransactionAmountException("Amount must be greater than zero.");
    }
  }

  /**
   * Attempts to lock the given account.
   * Uses standard blocking acquisition, guaranteed deadlock-free via global ID ordering.
   *
   * @param account The account to lock
   * @return true if the account was successfully locked
   * @throws InterruptedException If the thread is interrupted while acquiring the lock
   */
  private boolean attemptToLockAccount(Account account) throws InterruptedException {
    account.lock();
    return true;
  }

  /**
   * Performs the transaction by withdrawing funds from the source account and depositing them into the destination account.
   *
   * @param sourceAccount The account from which funds will be withdrawn
   * @param destinationAccount The account to which funds will be deposited
   * @param transferAmount The amount to be transferred
   * @throws InsufficientBalanceException If the source account does not have enough balance to complete the withdrawal
   * @throws InvalidTransactionAmountException If the transfer amount is invalid (zero or negative)
   */
  private void performTransaction(Account sourceAccount, Account destinationAccount, BigDecimal transferAmount)
          throws InsufficientBalanceException, InvalidTransactionAmountException {

    withdrawFromAccount(sourceAccount, transferAmount);
    depositToAccount(destinationAccount, transferAmount);
  }

  /**
   * Withdraws the specified amount from the given account.
   *
   * @param sourceAccount The account to withdraw from
   * @param transferAmount The amount to withdraw
   * @throws InsufficientBalanceException If the account balance is insufficient for the withdrawal
   */
  private void withdrawFromAccount(Account sourceAccount, BigDecimal transferAmount)
          throws InsufficientBalanceException {

    if (sourceAccount.getBalance().compareTo(transferAmount) < 0) {
      throw new InsufficientBalanceException(String.format("Insufficient funds: attempted to withdraw %s, but balance is %s",
              transferAmount, sourceAccount.getBalance()));
    }
    sourceAccount.setBalance(sourceAccount.getBalance().subtract(transferAmount));
  }

  /**
   * Deposits the specified amount into the given account.
   *
   * @param destinationAccount The account to deposit into
   * @param transferAmount The amount to deposit
   * @throws InvalidTransactionAmountException If the transfer amount is invalid (zero or negative)
   */
  private void depositToAccount(Account destinationAccount, BigDecimal transferAmount)
          throws InvalidTransactionAmountException {

    destinationAccount.setBalance(destinationAccount.getBalance().add(transferAmount));
  }

  /**
   * Releases the lock on the given account if the lock was acquired.
   *
   * @param account The account whose lock needs to be released
   * @param lockAcquired true if the lock was successfully acquired, false otherwise
   */
  private void releaseLockIfHeld(Account account, boolean lockAcquired) {
    if (lockAcquired) {
      account.unlock();
    }
  }

  /**
   * Retrieves an account by its ID or throws an exception if the account does not exist.
   *
   * @param accountId The account ID
   * @return The account corresponding to the accountId
   * @throws InvalidAccountException If the account with the given ID does not exist
   */
  private Account retrieveAccountOrThrow(String accountId) throws InvalidAccountException {
    Account account = accountsRepository.getAccount(accountId);
    if (account == null) {
      log.warn("Account with id {} not found", accountId);
      throw new InvalidAccountException(String.format("Account not found for id %s", accountId));
    }
    return account;
  }

  /**
   * Sends a notification about the successful transfer to the users involved.
   *
   * @param sourceAccount The source account from which the money is withdrawn
   * @param destinationAccount The destination account to which the money is deposited
   * @param transferAmount The amount transferred
   */
  private void notifyTransferCompletion(Account sourceAccount, Account destinationAccount, BigDecimal transferAmount) {
      String sourceMessage = String.format("Transferred %s to account %s",
              transferAmount.toPlainString(), destinationAccount.getAccountId());
      String destinationMessage = String.format("Received %s from account %s",
              transferAmount.toPlainString(), sourceAccount.getAccountId());

      try {
          notificationService.notifyAboutTransfer(sourceAccount, sourceMessage);
      } catch (Exception e) {
          log.error("Failed to notify source account {}: {}", sourceAccount.getAccountId(), e.getMessage());
      }

      try {
          notificationService.notifyAboutTransfer(destinationAccount, destinationMessage);
      } catch (Exception e) {
          log.error("Failed to notify destination account {}: {}", destinationAccount.getAccountId(), e.getMessage());
      }
  }

}
