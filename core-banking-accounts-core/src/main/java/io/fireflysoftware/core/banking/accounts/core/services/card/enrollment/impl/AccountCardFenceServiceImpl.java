/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceAuthority.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardEnrollmentRules.Owner;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceException.Reason;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import org.fireflyframework.core.transaction.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

public final class AccountCardFenceServiceImpl implements AccountCardFenceService {
    private final AccountCardFenceAuthority authority;
    private final AccountCardFenceSession command, reader;
    private final AccountCardFenceAuditImpl audit;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Supplier<UUID> ids;

    public AccountCardFenceServiceImpl(AccountCardFenceAuthority authority, AccountCardFenceSession command,
            AccountCardFenceSession reader, Clock clock, LongSupplier nanos, Supplier<UUID> ids) {
        this.authority = Objects.requireNonNull(authority);this.command = Objects.requireNonNull(command);
        this.reader = Objects.requireNonNull(reader);this.clock = Objects.requireNonNull(clock);
        this.nanos = Objects.requireNonNull(nanos);this.ids = Objects.requireNonNull(ids);
        if (command.role() != AccountCardFenceSession.Role.COMMAND || reader.role() != AccountCardFenceSession.Role.READER
                || command.operations() == reader.operations() || command.resources().factory() == reader.resources().factory())
            throw new IllegalArgumentException("DISTINCT_OWNED_ACCOUNT_FENCE_ROLE_SESSIONS_REQUIRED");
        this.audit = new AccountCardFenceAuditImpl(command, authority, clock, nanos, ids);
    }

    private record Intent(UUID fence, UUID operation, Purpose purpose, UUID receipt, Long version,
            String reason, Long deadline, Acquire acquire, Claim claim, Cancel cancel) {}
    private record Retained(AccountCardFence fence, AccountCardFenceAction action, boolean replay) {}

    public Mono<Observation> acquire(UUID account, Acquire request) {
        return Mono.defer(() -> {
            AccountCardEnrollmentRules.validate(request);
            return execute(account, new Intent(request.actionId(), request.actionId(), Purpose.ACQUIRE, null,
                    request.expectedAccountVersion(), request.reasonCode(), request.acceptNotAfterEpochMillis(), request, null, null));
        });
    }
    public Mono<Observation> claim(UUID account, UUID fence, Claim request) {
        return Mono.defer(() -> {
            if (request == null) throw new IllegalArgumentException("CLAIM_REQUEST_REQUIRED");
            return execute(account, new Intent(fence, request.claimActionId(), Purpose.CLAIM, request.receiptId(),
                    request.expectedFenceVersion(), "CARD_ENROLLMENT_CLAIM", null, null, request, null));
        });
    }
    public Mono<Observation> cancel(UUID account, UUID fence, Cancel request) {
        return Mono.defer(() -> {
            if (request == null) throw new IllegalArgumentException("CANCELLATION_REQUEST_REQUIRED");
            return execute(account, new Intent(fence, request.cancellationActionId(), Purpose.CANCEL, request.receiptId(),
                    request.expectedFenceVersion(), request.reasonCode(), null, null, null, request));
        });
    }
    public Mono<Observation> recover(UUID account, UUID fence, UUID action) {
        return Mono.defer(() -> execute(account, new Intent(fence, action, Purpose.READ, null, null, null, null, null, null, null)));
    }

    private Mono<Observation> execute(UUID account, Intent intent) {
        requireId(account);requireId(intent.fence());requireId(intent.operation());
        var window = new AccountCardFenceWindow(clock, nanos);
        return authority.authorize(account, intent.purpose()).switchIfEmpty(Mono.error(failure(intent, Reason.REFUSED)))
                .timeout(window.remaining()).flatMap(permit -> {
                    requireId(permit.tenantId());requireId(permit.actorId());requireId(permit.partyId());
                    if (!account.equals(permit.accountId()) || intent.purpose() != permit.purpose()) throw failure(intent, Reason.REFUSED);
                    window.constrain(permit);
                    var session = intent.purpose() == Purpose.READ ? reader : command;
                    var definition = new DefaultTransactionDefinition();
                    definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                    definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                    // READ uses only a private technical context; reader has SELECT-only business grants.
                    definition.setReadOnly(false);
                    UUID nonce = nextId();Instant until = window.commandUntil(permit);
                    var workFailure = new AtomicReference<Throwable>();
                    return session.transactions().execute(window.budget(), definition, scope -> Mono.defer(() -> {
                        long before = window.beforeNative();var actor = permit.auditActor();
                        return session.nativeRepository().enter(nonce, permit.tenantId(), permit.actorId(), permit.partyId(), account,
                                intent.fence(), intent.operation(), intent.purpose().name(), intent.receipt(), intent.version(), intent.reason(), intent.deadline(),
                                actor.issuer(), actor.subject(), actor.clientId(), actor.platformTenantId(), permit.policyRevision(),
                                permit.effectiveUntil().atOffset(ZoneOffset.UTC), until.atOffset(ZoneOffset.UTC))
                            .switchIfEmpty(Mono.error(failure(intent, Reason.UNAVAILABLE))).flatMap(stamp -> {
                                window.installed(nonce, until, stamp, before, scope);
                                return new AccountCardFenceOwnerStoreImpl(session.operations()).read(permit.tenantId(), account, permit.partyId())
                                    .switchIfEmpty(Mono.error(failure(intent, Reason.REFUSED))).flatMap(owner ->
                                        work(session, permit, owner, intent, stamp.databaseTime()).flatMap(retained ->
                                            authority.requireCurrent(permit).then(intent.purpose() == Purpose.READ ? Mono.empty()
                                                : audit.append(scope, nonce, permit, retained.fence(), retained.action(), retained.replay()))
                                            .then(session.nativeRepository().complete(nonce))
                                            .filter(nonce::equals).switchIfEmpty(Mono.error(failure(intent, Reason.UNAVAILABLE)))
                                            .map(ignored -> new Observation(retained.fence().receipt(), retained.action().receipt(), owner.accountVersion(),
                                                AccountCardEnrollmentRules.eligible(retained.fence().receipt(), owner, stamp.databaseTime()), stamp.databaseTime(),
                                                Collections.min(List.of(permit.effectiveUntil(), stamp.commandUntil(), Instant.ofEpochMilli(retained.fence().acceptNotAfterEpochMillis())))))));
                            });
                    }).doOnError(error -> workFailure.compareAndSet(null, error)))
                    .onErrorMap(RootTransactionException.class, error -> transactionFailure(intent, error, workFailure.get()))
                    .flatMap(completion -> {
                        if (!completion.transactionFacts().boundaryCompleted() || !completion.transactionFacts().commitAcknowledged())
                            return Mono.error(new AccountCardFenceException(intent.operation(), Reason.UNKNOWN, completion.transactionFacts(), completion.cleanupFact()));
                        return authority.requireCurrent(permit).then(Mono.fromCallable(() -> { window.remaining();return completion.value(); }))
                                .onErrorMap(error -> new AccountCardFenceException(intent.operation(), Reason.WITHHELD, completion.transactionFacts(), completion.cleanupFact()));
                    });
                }).onErrorMap(error -> error instanceof AccountCardFenceException ? error : failure(intent, authorityRefusal(error) ? Reason.REFUSED : Reason.UNAVAILABLE));
    }

    private Mono<Retained> work(AccountCardFenceSession session, Permit permit, Owner owner, Intent intent, Instant time) {
        return session.store().action(permit.tenantId(), permit.accountId(), intent.operation()).flatMap(action ->
                session.store().fence(permit.tenantId(), permit.accountId(), intent.fence())
                    .switchIfEmpty(Mono.error(failure(intent, Reason.NOT_FOUND))).map(fence -> {
                        requireReplay(permit, intent, fence, action);return new Retained(fence, action, true);
                    })).switchIfEmpty(Mono.defer(() -> {
                        if (intent.purpose() == Purpose.READ) return Mono.error(failure(intent, Reason.NOT_FOUND));
                        Mono<AccountCardFence> mutation;
                        if (intent.purpose() == Purpose.ACQUIRE) {
                            AccountCardEnrollmentRules.admit(owner, permit.tenantId(), permit.partyId(), permit.accountId(), intent.acquire(), time);
                            var fence = new AccountCardFence(intent.fence(), nextId(), permit.tenantId(), permit.actorId(), permit.partyId(), permit.accountId(),
                                    owner.contractId(), owner.mandateId(), owner.accountVersion(), owner.currency(), intent.reason(), intent.deadline(), time, "HELD", 0, null, null);
                            mutation = session.store().insert(fence);
                        } else {
                            mutation = session.store().fence(permit.tenantId(), permit.accountId(), intent.fence())
                                .switchIfEmpty(Mono.error(failure(intent, Reason.NOT_FOUND))).flatMap(fence -> {
                                    var next = intent.purpose() == Purpose.CLAIM ? AccountCardEnrollmentRules.claim(fence.receipt(), intent.claim(), owner, time)
                                            : AccountCardEnrollmentRules.cancel(fence.receipt(), intent.cancel());
                                    return session.store().transition(fence, AccountCardFence.from(next));
                                });
                        }
                        return mutation.flatMap(fence -> {
                            var actor = permit.auditActor();
                            var action = new AccountCardFenceAction(intent.operation(), nextId(), fence.actionId(), fence.receiptId(), fence.tenantId(), fence.actorId(),
                                    fence.partyId(), fence.accountId(), intent.purpose().name(), intent.version(), intent.reason(), fence.state(), fence.version(), time, nextId(),
                                    actor.issuer(), actor.subject(), actor.clientId(), actor.platformTenantId(), permit.policyRevision());
                            return session.store().append(action).map(saved -> new Retained(fence, saved, false));
                        });
                    }));
    }

    private static void requireReplay(Permit permit, Intent intent, AccountCardFence fence, AccountCardFenceAction action) {
        if (!action.fenceActionId().equals(fence.actionId()) || !action.fenceReceiptId().equals(fence.receiptId())
                || !action.actorId().equals(permit.actorId()) || !fence.actorId().equals(permit.actorId())
                || !action.partyId().equals(permit.partyId())) throw new AccountCardEnrollmentRules.Conflict();
        if (intent.purpose() == Purpose.READ) return;
        if (!action.operation().equals(intent.purpose().name()) || action.expectedVersion() != intent.version()
                || !action.reasonCode().equals(intent.reason())) throw new AccountCardEnrollmentRules.Conflict();
        if (intent.purpose() == Purpose.ACQUIRE) AccountCardEnrollmentRules.requireAcquireReplay(fence.receipt(), intent.acquire(),
                permit.tenantId(), permit.actorId(), permit.partyId(), permit.accountId());
        else if (!fence.receiptId().equals(intent.receipt())) throw new AccountCardEnrollmentRules.Conflict();
    }

    private UUID nextId() { UUID id = ids.get();requireId(id);return id; }
    private static void requireId(UUID id) { if (id == null || id.equals(new UUID(0, 0))) throw new IllegalArgumentException("EXPLICIT_NONZERO_ACTION_AND_SCOPE_REQUIRED"); }
    private static AccountCardFenceException failure(Intent intent, Reason reason) {
        return new AccountCardFenceException(intent.operation(), reason, new TransactionFacts(false, false, false, false), CleanupFact.COMPLETE);
    }
    private static AccountCardFenceException transactionFailure(Intent intent, RootTransactionException error, Throwable work) {
        var facts = error.transactionFacts();Reason reason = facts.commitAcknowledged() ? Reason.WITHHELD : Reason.UNKNOWN;
        if (!facts.boundaryCompleted() && !facts.commitAcknowledged() && facts.rollbackAcknowledged() && error.cleanupFact() == CleanupFact.COMPLETE) {
            reason = work instanceof AccountCardEnrollmentRules.Conflict || serializationConflict(work) ? Reason.CONFLICT
                    : work instanceof AccountCardEnrollmentRules.Refused || authorityRefusal(work) ? Reason.REFUSED
                    : work instanceof AccountCardFenceException business ? business.reason() : Reason.UNAVAILABLE;
        }
        return new AccountCardFenceException(intent.operation(), reason, facts, error.cleanupFact(), work);
    }
    private static boolean authorityRefusal(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.web.server.ResponseStatusException status && status.getStatusCode().value() == 403) return true;
            if (cause instanceof io.r2dbc.spi.R2dbcException database && "42501".equals(database.getSqlState())) return true;
        }
        return false;
    }
    private static boolean serializationConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof io.r2dbc.spi.R2dbcException database && "40001".equals(database.getSqlState())) return true;
        return false;
    }
}
