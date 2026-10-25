package io.fireflysoftware.core.banking.accounts.models.entities.funds;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("account_money_enrollment")
public record AccountMoneyEnrollment(
        @Id UUID accountId,
        String tenantId,
        UUID enrollmentId,
        UUID contractId,
        String currency,
        String kind,
        String mode,
        String control,
        Long version,
        UUID pendingActionId) {}
