/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import java.util.UUID;
import org.fireflyframework.core.transaction.CleanupFact;
import org.fireflyframework.core.transaction.TransactionFacts;

public final class AccountCardFenceException extends RuntimeException {
    public enum Reason { CONFLICT, REFUSED, NOT_FOUND, UNAVAILABLE, UNKNOWN, WITHHELD }
    private final UUID operationActionId;
    private final Reason reason;
    private final TransactionFacts transactionFacts;
    private final CleanupFact cleanupFact;

    public AccountCardFenceException(UUID operationActionId, Reason reason,
            TransactionFacts transactionFacts, CleanupFact cleanupFact) {
        this(operationActionId, reason, transactionFacts, cleanupFact, null);
    }

    public AccountCardFenceException(UUID operationActionId, Reason reason,
            TransactionFacts transactionFacts, CleanupFact cleanupFact, Throwable cause) {
        super("ACCOUNT_CARD_ENROLLMENT_" + reason.name(), cause);
        this.operationActionId = operationActionId;this.reason = reason;
        this.transactionFacts = transactionFacts;this.cleanupFact = cleanupFact;
    }
    public UUID operationActionId() { return operationActionId; }
    public Reason reason() { return reason; }
    public TransactionFacts transactionFacts() { return transactionFacts; }
    public CleanupFact cleanupFact() { return cleanupFact; }
}
