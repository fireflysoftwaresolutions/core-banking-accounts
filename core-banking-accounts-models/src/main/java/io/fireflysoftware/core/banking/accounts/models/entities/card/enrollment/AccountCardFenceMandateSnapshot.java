/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Read-only mapping of the existing VARCHAR mandate role used by the owning admission query. */
@Table("servicing_mandate")
public record AccountCardFenceMandateSnapshot(@Id UUID servicingMandateId, UUID accountId,
        UUID partyId, String role, boolean active) {}
