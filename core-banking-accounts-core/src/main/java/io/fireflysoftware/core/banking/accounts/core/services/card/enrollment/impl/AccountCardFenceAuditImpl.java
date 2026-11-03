/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceAuthority.Permit;
import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.*;
import java.time.Clock;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.*;
import org.fireflyframework.core.transaction.RootTransactionScope;
import org.fireflyframework.kernel.audit.*;
import reactor.core.publisher.Mono;

final class AccountCardFenceAuditImpl {
    private final AccountCardFenceSession session;
    private final AccountCardFenceAuthority authority;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Supplier<UUID> ids;

    AccountCardFenceAuditImpl(AccountCardFenceSession session, AccountCardFenceAuthority authority,
            Clock clock, LongSupplier nanos, Supplier<UUID> ids) {
        this.session = session;this.authority = authority;this.clock = clock;this.nanos = nanos;this.ids = ids;
    }

    Mono<Void> append(RootTransactionScope scope, UUID contextNonce, Permit permit,
            AccountCardFence fence, AccountCardFenceAction action, boolean replay) {
        var resources = session.resources();var template = session.operations();var client = template.getDatabaseClient();
        return resources.factory().requireBoundScope(resources, scope).then(Mono.defer(() -> {
            AuditEvent event = AccountCardFenceAuditEvidence.event(fence, action);
            if (!event.actor().equals(permit.auditActor())) return Mono.error(invalid());
            AuditAppendAuthority admission = retained -> authority.requireCurrent(permit)
                    .then(Mono.fromCallable(() -> {
                        if (!retained.equals(event)) throw invalid();
                        return new AuditAppendAuthority.Permit(event.stream(), event.actor(), permit.effectiveUntil());
                    }));
            UUID nonce = ids.get();
            var append = new SpringDataTransactionalAuditAppenderImpl(resources, client, template, resources.manager(), admission, clock, nanos, () -> nonce);
            var completed = new SpringDataCompletedAuditAppendServiceImpl(resources, client, template, resources.manager(), clock, nanos);
            return append.append(scope, event).flatMap(provisional -> {
                if (provisional.replay() != replay) return Mono.error(invalid());
                return completed.requireCompleted(scope, nonce, provisional).switchIfEmpty(Mono.error(invalid())).flatMap(record -> {
                    if (!record.event().equals(event) || !record.equals(AuditRecordHasher.record(
                            new AuditCheckpoint(event.stream(), record.sequence()-1, record.previousHash()), event, record.recordedAt())))
                        return Mono.error(invalid());
                    var expected = new AccountCardFenceAudit(action.operationActionId(), fence.tenantId(), event.eventId(), nonce, record.sequence(), record.recordHash());
                    Mono<AccountCardFenceAudit> binding = replay ? session.store().audit(fence.tenantId(), action.operationActionId())
                            .filter(row -> row.eventId().equals(expected.eventId()) && row.auditSequence() == expected.auditSequence()
                                    && row.auditRecordHash().equals(expected.auditRecordHash())) : session.store().appendAudit(expected);
                    return binding.switchIfEmpty(Mono.error(invalid())).then(session.nativeRepository().pairAudit(contextNonce,
                            nonce, event.eventId(), record.sequence(), record.recordHash(), replay))
                            .filter(contextNonce::equals).switchIfEmpty(Mono.error(invalid())).then();
                });
            });
        })).onErrorResume(error -> resources.factory().markBoundScopeRollbackOnly(resources, scope)
                .onErrorResume(markFailure -> Mono.error(error)).then(Mono.error(error)));
    }

    private static IllegalStateException invalid() { return new IllegalStateException("ORIGINAL_COMPLETED_ACCOUNT_FENCE_AUDIT_REQUIRED"); }
}
