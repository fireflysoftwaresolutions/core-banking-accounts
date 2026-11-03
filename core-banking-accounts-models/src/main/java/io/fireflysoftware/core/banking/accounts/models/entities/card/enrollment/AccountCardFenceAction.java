/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("account_card_enrollment_action")
public record AccountCardFenceAction(@Id UUID operationActionId, UUID operationReceiptId,
        UUID fenceActionId, UUID fenceReceiptId, UUID tenantId, UUID actorId, UUID partyId,
        UUID accountId, String operation, long expectedVersion, String reasonCode,
        String resultingState, long resultingVersion, Instant recordedAt, UUID eventId,
        String actorIssuer, String actorSubject, String actorClientId, String platformTenantId,
        String policyRevision) {
    public ActionReceipt receipt() {
        return new ActionReceipt(operationActionId, operationReceiptId, Operation.valueOf(operation),
                fenceActionId, fenceReceiptId, State.valueOf(resultingState), resultingVersion, recordedAt);
    }
}
