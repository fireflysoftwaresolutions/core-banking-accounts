/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

public interface AccountCardFenceAuthority {
    enum Purpose { ACQUIRE, CLAIM, CANCEL, READ }
    record Permit(SecurityPrincipal principal, UUID tenantId, UUID actorId, UUID partyId,
            UUID accountId, Purpose purpose, AuditActor auditActor, String policyRevision,
            Instant observedAt, Instant effectiveUntil) {}

    Mono<Permit> authorize(UUID accountId, Purpose purpose);
    Mono<Void> requireCurrent(Permit permit);
}
