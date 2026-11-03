/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.models.repositories.card.enrollment.impl;

import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;

/** Uses the caller's exact transaction-bound mapping and callback graph. */
public final class AccountCardFenceStoreImpl {
    private final R2dbcEntityTemplate operations;

    public AccountCardFenceStoreImpl(R2dbcEntityTemplate operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    public R2dbcEntityTemplate operations() { return operations; }

    public Mono<AccountCardFence> fence(UUID tenant, UUID account, UUID action) {
        return operations.selectOne(Query.query(Criteria.where("tenant_id").is(tenant)
                .and("account_id").is(account).and("action_id").is(action)), AccountCardFence.class);
    }

    public Mono<AccountCardFenceAction> action(UUID tenant, UUID account, UUID action) {
        return operations.selectOne(Query.query(Criteria.where("tenant_id").is(tenant)
                .and("account_id").is(account).and("operation_action_id").is(action)), AccountCardFenceAction.class);
    }

    public Mono<AccountCardFence> insert(AccountCardFence expected) {
        return operations.insert(expected).filter(expected::equals).switchIfEmpty(Mono.error(changed()))
                .then(fence(expected.tenantId(), expected.accountId(), expected.actionId()))
                .filter(expected::equals).switchIfEmpty(Mono.error(changed()));
    }

    public Mono<AccountCardFence> transition(AccountCardFence original, AccountCardFence expected) {
        var query = Query.query(Criteria.where("tenant_id").is(original.tenantId())
                .and("account_id").is(original.accountId()).and("action_id").is(original.actionId())
                .and("receipt_id").is(original.receiptId()).and("state").is(original.state())
                .and("version").is(original.version()));
        var update = Update.update("state", expected.state()).set("version", expected.version())
                .set("claim_action_id", expected.claimActionId()).set("cancellation_action_id", expected.cancellationActionId());
        return operations.update(query, update, AccountCardFence.class)
                .filter(count -> count == 1).switchIfEmpty(Mono.error(changed()))
                .then(fence(expected.tenantId(), expected.accountId(), expected.actionId()))
                .filter(expected::equals).switchIfEmpty(Mono.error(changed()));
    }

    public Mono<AccountCardFenceAction> append(AccountCardFenceAction expected) {
        return operations.insert(expected).filter(expected::equals).switchIfEmpty(Mono.error(changed()))
                .then(action(expected.tenantId(), expected.accountId(), expected.operationActionId()))
                .filter(expected::equals).switchIfEmpty(Mono.error(changed()));
    }

    public Mono<AccountCardFenceAudit> audit(UUID tenant, UUID action) {
        return operations.selectOne(Query.query(Criteria.where("tenant_id").is(tenant)
                .and("operation_action_id").is(action)), AccountCardFenceAudit.class);
    }

    public Mono<AccountCardFenceAudit> appendAudit(AccountCardFenceAudit expected) {
        return operations.insert(expected).filter(expected::equals).switchIfEmpty(Mono.error(changed()))
                .then(audit(expected.tenantId(), expected.operationActionId()))
                .filter(expected::equals).switchIfEmpty(Mono.error(changed()));
    }

    private static IllegalStateException changed() {
        return new IllegalStateException("EXACT_MAPPED_ACCOUNT_CARD_FENCE_WRITE_REQUIRED");
    }
}
