/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardEnrollmentRules.Owner;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;
import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.AccountCardFenceMandateSnapshot;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Mono;

/** Reads real owner rows after the native account and shared enrollment locks are held. */
final class AccountCardFenceOwnerStoreImpl {
    private final R2dbcEntityTemplate operations;
    AccountCardFenceOwnerStoreImpl(R2dbcEntityTemplate operations) { this.operations = operations; }

    Mono<Owner> read(UUID tenant, UUID account, UUID party) {
        var accountQuery = Query.query(Criteria.where("account_id").is(account));
        var enrollmentQuery = Query.query(Criteria.where("account_id").is(account).and("tenant_id").is(tenant.toString()));
        var mandateQuery = Query.query(Criteria.where("account_id").is(account).and("party_id").is(party)
                .and("role").is("OWNER").and("active").is(true));
        return operations.selectOne(accountQuery, FundsAccountSnapshot.class).flatMap(accountRow ->
                operations.selectOne(enrollmentQuery, AccountMoneyEnrollment.class).flatMap(enrollment ->
                operations.select(mandateQuery, AccountCardFenceMandateSnapshot.class).single().map(mandate -> {
                    if (!accountRow.contractId().equals(enrollment.contractId()) || !accountRow.currency().equals(enrollment.currency()))
                        throw new IllegalStateException("CURRENT_CANONICAL_ACCOUNT_BINDING_REQUIRED");
                    return new Owner(enrollment.tenantId(), accountRow.accountId(), accountRow.contractId(), accountRow.currency(),
                            enrollment.kind(), enrollment.mode(), enrollment.control(), enrollment.version(), enrollment.pendingActionId(),
                            accountRow.accountStatus().name(), accountRow.closeDate(), mandate.servicingMandateId(), mandate.partyId(),
                            mandate.active(), mandate.role());
                })));
    }
}
