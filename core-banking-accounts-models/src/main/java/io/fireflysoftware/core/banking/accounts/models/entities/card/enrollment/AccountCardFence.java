/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("account_card_enrollment_fence")
public record AccountCardFence(@Id UUID actionId, UUID receiptId, UUID tenantId, UUID actorId,
        UUID partyId, UUID accountId, UUID contractId, UUID mandateId, long accountVersion,
        String currency, String reasonCode, long acceptNotAfterEpochMillis, Instant recordedAt,
        String state, long version, UUID claimActionId, UUID cancellationActionId) {
    public Fence receipt() {
        return new Fence(actionId, receiptId, tenantId, actorId, partyId, accountId, contractId,
                mandateId, accountVersion, currency, reasonCode, acceptNotAfterEpochMillis,
                recordedAt, State.valueOf(state), version, claimActionId, cancellationActionId);
    }

    public static AccountCardFence from(Fence f) {
        return new AccountCardFence(f.actionId(), f.receiptId(), f.tenantId(), f.actorId(), f.partyId(),
                f.accountId(), f.contractId(), f.mandateId(), f.accountVersion(), f.currency(),
                f.reasonCode(), f.acceptNotAfterEpochMillis(), f.recordedAt(), f.state().name(),
                f.version(), f.claimActionId(), f.cancellationActionId());
    }
}
