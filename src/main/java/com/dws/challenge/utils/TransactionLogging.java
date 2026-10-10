package com.dws.challenge.utils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public class TransactionLogging {

    /**
     * Logs the transfer details, including the amount, from and to account information,
     * and the balance of both accounts involved in the transaction.
     *
     * @param transferAmount The amount being transferred
     * @param sourceAccountId The ID of the source account
     * @param destinationAccountId The ID of the destination account
     * @param sourceAccountBalance The balance of the source account after the transfer
     * @param destinationAccountBalance The balance of the destination account after the transfer
     */
    public static void logTransferDetails(final BigDecimal transferAmount, final String sourceAccountId,
                                          final String destinationAccountId, final BigDecimal sourceAccountBalance,
                                          final BigDecimal destinationAccountBalance) {

        String logMessageTemplate = "{} transferred {} from {} to {}. Source Account balance: {}. Destination Account balance: {}";

        // Get the name of the current thread for logging purposes
        String currentThreadName = Thread.currentThread().getName();

        // Check for null values in parameters and log a warning if any are found
        if (transferAmount == null || sourceAccountId == null || destinationAccountId == null ||
                sourceAccountBalance == null || destinationAccountBalance == null) {
            log.warn("Null parameter detected in logTransferDetails: transferAmount={}, sourceAccountId={}, destinationAccountId={}, " +
                            "sourceAccountBalance={}, destinationAccountBalance={}", transferAmount, sourceAccountId, destinationAccountId,
                    sourceAccountBalance, destinationAccountBalance);
        }

        // Log the message at the debug level if debugging is enabled
        if (log.isDebugEnabled()) {
            log.debug(logMessageTemplate, currentThreadName,
                    transferAmount != null ? transferAmount : "N/A",
                    sourceAccountId != null ? sourceAccountId : "N/A",
                    destinationAccountId != null ? destinationAccountId : "N/A",
                    sourceAccountBalance != null ? sourceAccountBalance : "N/A",
                    destinationAccountBalance != null ? destinationAccountBalance : "N/A");
        }
    }
}
