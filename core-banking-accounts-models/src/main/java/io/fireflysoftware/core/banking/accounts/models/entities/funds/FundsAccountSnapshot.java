package io.fireflysoftware.core.banking.accounts.models.entities.funds;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

@Table("account")
public record FundsAccountSnapshot(
        @Id UUID accountId,
        UUID contractId,
        String currency,
        FundsAccountStatus accountStatus,
        LocalDate closeDate) {}
