package io.fireflysoftware.core.banking.accounts.models.entities.funds;

/** PostgreSQL labels for the managed account/status-history persistence projections. */
public enum FundsAccountStatus {
    DRAFT,
    OPEN,
    CLOSED,
    SUSPENDED,
    DORMANT
}
