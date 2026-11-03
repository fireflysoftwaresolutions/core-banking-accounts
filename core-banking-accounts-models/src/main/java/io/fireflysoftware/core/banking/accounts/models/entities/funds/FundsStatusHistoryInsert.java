package io.fireflysoftware.core.banking.accounts.models.entities.funds;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** Managed-control insert projection; technical audit timestamps retain database defaults. */
@Table("account_status_history")
public record FundsStatusHistoryInsert(
        @Id UUID accountStatusHistoryId,
        UUID accountId,
        FundsAccountStatus statusCode,
        LocalDateTime statusStartDatetime,
        String reason) {}
