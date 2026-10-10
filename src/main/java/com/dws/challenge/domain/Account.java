package com.dws.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class Account {

  @JsonIgnore
  private final Lock lock = new ReentrantLock();

  @NotNull
  @NotEmpty
  private final String accountId;

  @NotNull
  @Min(value = 0, message = "Initial balance must be positive.")
  private BigDecimal balance;

  public Account(String accountId) {
    this.accountId = accountId;
    this.balance = BigDecimal.ZERO;
  }

  @JsonCreator
  public Account(@JsonProperty("accountId") String accountId,
    @JsonProperty("balance") BigDecimal balance) {
    this.accountId = accountId;
    this.balance = balance;
  }

  public void lock() {
    lock.lock();
  }

  public boolean tryResourceLock(long time, TimeUnit unit, int maxRetries) throws InterruptedException {
    for (int i = 0; i < maxRetries; i++) {
      if (lock.tryLock(time, unit)) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  public void unlock() {
    lock.unlock();
  }
}
