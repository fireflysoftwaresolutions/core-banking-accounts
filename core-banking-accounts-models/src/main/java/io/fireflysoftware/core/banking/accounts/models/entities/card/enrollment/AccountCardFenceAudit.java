/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("account_card_enrollment_audit")
public record AccountCardFenceAudit(@Id UUID operationActionId, UUID tenantId, UUID eventId,
        UUID auditContextNonce, long auditSequence, String auditRecordHash) {}
