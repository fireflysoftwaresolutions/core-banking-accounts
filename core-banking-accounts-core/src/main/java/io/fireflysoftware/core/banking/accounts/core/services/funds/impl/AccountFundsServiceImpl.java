/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.banking.accounts.core.services.funds.impl;

import io.fireflysoftware.core.banking.accounts.core.services.funds.AccountFundsService;
import io.fireflysoftware.core.banking.accounts.core.services.funds.LedgerFundsPort;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;
import io.fireflysoftware.core.banking.accounts.models.repositories.funds.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.UUID;

@Service("accountFundsService")
public class AccountFundsServiceImpl implements AccountFundsService {
    private final AccountMoneyEnrollmentRepository enrollments;
    private final AccountMoneyActionRepository actions;
    private final FundsNativeRepository nativeRepository;
    private final FundsDatabaseContext context;
    private final TransactionalOperator tx;
    private final ObjectProvider<LedgerFundsPort> ledger;

    public AccountFundsServiceImpl(
            AccountMoneyEnrollmentRepository enrollments,
            AccountMoneyActionRepository actions,
            FundsNativeRepository nativeRepository,
            ReactiveTransactionManager manager,
            ObjectProvider<LedgerFundsPort> ledger,
            FundsDatabaseContext context) {
        this.enrollments = enrollments;
        this.actions = actions;
        this.nativeRepository = nativeRepository;
        this.context = context;
        this.tx = TransactionalOperator.create(manager);
        this.ledger = ledger;
    }

    @Transactional
    public Mono<Enrollment> begin(SecurityPrincipal p, String tenant, UUID account, Begin input) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:enroll", account);
                    if (input == null || input.enrollmentId() == null || input.kind() == null)
                        return Mono.error(fail(400, "ENROLLMENT_ID_AND_KIND_REQUIRED"));
                    FundsPolicy.require(p, tenant, "funds:tenant-bind", account);
                    if (input.kind() == Kind.BANK_CONTROL)
                        FundsPolicy.require(p, tenant, "funds:bank-enroll", account);
                    return context.establish(p, tenant, "ENROLL", account)
                            .then(lockAccount(account))
                            .doOnNext(
                                    row ->
                                            FundsPolicy.enrollment(
                                                    p,
                                                    tenant,
                                                    account,
                                                    row.contractId(),
                                                    row.currency(),
                                                    input.kind().name()))
                            .then(nativeRepository.lockEnrollmentGate(account.toString()))
                            .then(
                                    nativeRepository.admitFreshEnrollment(
                                            tenant,
                                            input.enrollmentId(),
                                            input.kind().name(),
                                            account))
                            .then(read(tenant, account, true))
                            .flatMap(
                                    current -> {
                                        if (!current.enrollmentId().equals(input.enrollmentId())
                                                || !current.kind().equals(input.kind().name()))
                                            return Mono.error(
                                                    fail(409, "ENROLLMENT_INTENT_CONFLICT"));
                                        return Mono.just(toContract(current));
                                    })
                            .onErrorMap(
                                    e ->
                                            e instanceof ResponseStatusException se
                                                            && se.getStatusCode().value() == 404
                                                    ? fail(409, "FRESH_OPEN_ACCOUNT_REQUIRED")
                                                    : e);
                });
    }

    @Transactional
    public Mono<Enrollment> link(SecurityPrincipal p, String tenant, UUID account, Link input) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:link", account);
                    return context.establish(p, tenant, "LINK", account)
                            .then(read(tenant, account, true))
                            .flatMap(
                                    row -> {
                                        if (input == null
                                                || !row.enrollmentId().equals(input.enrollmentId()))
                                            return Mono.error(
                                                    fail(409, "ENROLLMENT_LINK_CONFLICT"));
                                        if (row.mode().equals("MANAGED"))
                                            return Mono.just(toContract(row));
                                        return requireOne(
                                                        enrollments.linkEnrollment(row),
                                                        "ENROLLMENT_LINK_CONFLICT")
                                                .then(read(tenant, account, false))
                                                .map(this::toContract);
                                    });
                });
    }

    @Transactional(readOnly = true)
    public Mono<Enrollment> verify(SecurityPrincipal p, String tenant, UUID account) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:verify", account);
                    return context.establish(p, tenant, "VERIFY", account)
                            .then(read(tenant, account, false))
                            .map(this::toContract);
                });
    }

    private record ControlStep(Enrollment enrollment, boolean complete) {}

    public Mono<Enrollment> control(
            SecurityPrincipal p, String tenant, UUID account, Manage input) {
        return Mono.defer(
                () -> {
                    FundsPolicy.require(p, tenant, "funds:control", account);
                    if (input == null
                            || input.actionId() == null
                            || input.expectedVersion() == null
                            || input.expectedVersion() < 0
                            || input.target() == null
                            || !java.util.Set.of(Control.ACTIVE, Control.BLOCKED, Control.CLOSED)
                                    .contains(input.target()))
                        return Mono.error(fail(400, "EXPLICIT_ACCOUNT_MONEY_CONTROL_REQUIRED"));
                    return prepareControl(p, tenant, account, input)
                            .as(tx::transactional)
                            .flatMap(
                                    step -> {
                                        if (step.complete()) return Mono.just(step.enrollment());
                                        var port =
                                                ledger.getIfAvailable(
                                                        () ->
                                                                (t, e, a, c) ->
                                                                        Mono.error(
                                                                                fail(
                                                                                        503,
                                                                                        "LEDGER_FUNDS_CONTROL_NOT_CONFIGURED")));
                                        return port.apply(
                                                        tenant,
                                                        step.enrollment(),
                                                        input.actionId(),
                                                        input.target())
                                                .then(
                                                        Mono.defer(
                                                                () -> {
                                                                    FundsPolicy.require(
                                                                            p,
                                                                            tenant,
                                                                            "funds:control",
                                                                            account);
                                                                    return finishControl(
                                                                                    p, tenant,
                                                                                    account, input)
                                                                            .as(tx::transactional);
                                                                }))
                                                .onErrorResume(
                                                        LedgerFundsPort.ClosureRejected.class,
                                                        error -> {
                                                            FundsPolicy.require(
                                                                    p,
                                                                    tenant,
                                                                    "funds:control",
                                                                    account);
                                                            return completeControl(
                                                                            p,
                                                                            tenant,
                                                                            account,
                                                                            input,
                                                                            Control.BLOCKED,
                                                                            "REJECTED")
                                                                    .as(tx::transactional)
                                                                    .then(
                                                                            Mono.error(
                                                                                    fail(
                                                                                            409,
                                                                                            "ACCOUNT_CLOSURE_REJECTED_WITH_FUNDS")));
                                                        });
                                    });
                });
    }

    private Mono<FundsAccountSnapshot> lockAccount(UUID account) {
        return nativeRepository
                .lockAccount(account)
                .switchIfEmpty(Mono.error(fail(404, "ACCOUNT_NOT_FOUND")));
    }

    private Mono<ControlStep> prepareControl(
            SecurityPrincipal p, String tenant, UUID account, Manage input) {
        return context.establish(p, tenant, "CONTROL", account)
                .then(lockAccount(account))
                .flatMap(
                        snapshot ->
                                read(tenant, account, true)
                                        .flatMap(
                                                current -> {
                                                    if (!current.mode().equals("MANAGED"))
                                                        return Mono.error(
                                                                fail(
                                                                        409,
                                                                        "MANAGED_OPEN_MONEY_ACCOUNT_REQUIRED"));
                                                    return actions.findByIdTenantIdAndIdActionId(
                                                                    tenant, input.actionId())
                                                            .map(
                                                                    action -> {
                                                                        requireIntent(
                                                                                action, account,
                                                                                input);
                                                                        if (action.state()
                                                                                .equals("REJECTED"))
                                                                            throw fail(
                                                                                    409,
                                                                                    "ACCOUNT_CLOSURE_REJECTED_WITH_FUNDS");
                                                                        return new ControlStep(
                                                                                toContract(current),
                                                                                action.state()
                                                                                        .equals(
                                                                                                "COMPLETE"));
                                                                    })
                                                            .switchIfEmpty(
                                                                    Mono.defer(
                                                                            () -> {
                                                                                if (current.control()
                                                                                        .equals(
                                                                                                "CLOSED"))
                                                                                    return Mono
                                                                                            .error(
                                                                                                    fail(
                                                                                                            409,
                                                                                                            "CLOSED_ACCOUNT_MONEY_IS_FINAL"));
                                                                                if (!current.version()
                                                                                        .equals(
                                                                                                input
                                                                                                        .expectedVersion()))
                                                                                    return Mono
                                                                                            .error(
                                                                                                    fail(
                                                                                                            409,
                                                                                                            "STALE_ACCOUNT_MONEY_CONTROL"));
                                                                                if (current
                                                                                                .pendingActionId()
                                                                                        != null)
                                                                                    return Mono
                                                                                            .error(
                                                                                                    fail(
                                                                                                            409,
                                                                                                            "ACCOUNT_MONEY_CONTROL_PENDING"));
                                                                                String stage =
                                                                                        input
                                                                                                                .target()
                                                                                                        == Control
                                                                                                                .ACTIVE
                                                                                                ? "ACTIVE"
                                                                                                : input
                                                                                                                        .target()
                                                                                                                == Control
                                                                                                                        .BLOCKED
                                                                                                        ? "BLOCKING"
                                                                                                        : "CLOSING";
                                                                                return requireOne(
                                                                                                nativeRepository
                                                                                                        .claimAction(
                                                                                                                tenant,
                                                                                                                input
                                                                                                                        .actionId(),
                                                                                                                account,
                                                                                                                input.target()
                                                                                                                        .name(),
                                                                                                                input
                                                                                                                        .expectedVersion(),
                                                                                                                p
                                                                                                                        .subject())
                                                                                                        .map(
                                                                                                                Integer
                                                                                                                        ::longValue),
                                                                                                "ACCOUNT_CONTROL_SCOPE_CONFLICT")
                                                                                        .then(
                                                                                                input
                                                                                                                        .target()
                                                                                                                == Control
                                                                                                                        .ACTIVE
                                                                                                        ? requireOne(
                                                                                                                enrollments
                                                                                                                        .updateAccountStatus(
                                                                                                                                snapshot,
                                                                                                                                "OPEN",
                                                                                                                                null),
                                                                                                                "ACCOUNT_MONEY_CONTROL_CHANGED")
                                                                                                        : Mono
                                                                                                                .empty())
                                                                                        .then(
                                                                                                requireOne(
                                                                                                        enrollments
                                                                                                                .stageControl(
                                                                                                                        current,
                                                                                                                        input
                                                                                                                                .actionId(),
                                                                                                                        stage),
                                                                                                        "ACCOUNT_MONEY_CONTROL_CHANGED"))
                                                                                        .then(
                                                                                                read(
                                                                                                        tenant,
                                                                                                        account,
                                                                                                        false))
                                                                                        .map(
                                                                                                row ->
                                                                                                        new ControlStep(
                                                                                                                toContract(
                                                                                                                        row),
                                                                                                                false));
                                                                            }));
                                                }));
    }

    private Mono<Enrollment> finishControl(
            SecurityPrincipal p, String tenant, UUID account, Manage input) {
        return completeControl(p, tenant, account, input, input.target(), "COMPLETE");
    }

    private Mono<Enrollment> completeControl(
            SecurityPrincipal p,
            String tenant,
            UUID account,
            Manage input,
            Control finalState,
            String receiptState) {
        return context.establish(p, tenant, "CONTROL", account)
                .then(lockAccount(account))
                .flatMap(
                        snapshot ->
                                read(tenant, account, true)
                                        .flatMap(
                                                current ->
                                                        actions.findByIdTenantIdAndIdActionId(
                                                                        tenant, input.actionId())
                                                                .switchIfEmpty(
                                                                        Mono.error(
                                                                                fail(
                                                                                        409,
                                                                                        "ACCOUNT_MONEY_CONTROL_CHANGED")))
                                                                .flatMap(
                                                                        action -> {
                                                                            requireIntent(
                                                                                    action, account,
                                                                                    input);
                                                                            if (!input.actionId()
                                                                                    .equals(
                                                                                            current
                                                                                                    .pendingActionId())) {
                                                                                return action.state()
                                                                                                .equals(
                                                                                                        receiptState)
                                                                                        ? Mono.just(
                                                                                                toContract(
                                                                                                        current))
                                                                                        : Mono
                                                                                                .error(
                                                                                                        fail(
                                                                                                                409,
                                                                                                                "ACCOUNT_MONEY_CONTROL_CHANGED"));
                                                                            }
                                                                            if (!action.state()
                                                                                    .equals(
                                                                                            "PENDING"))
                                                                                return Mono.error(
                                                                                        fail(
                                                                                                409,
                                                                                                "ACCOUNT_MONEY_CONTROL_CHANGED"));
                                                                            String status =
                                                                                    finalState
                                                                                                    == Control
                                                                                                            .CLOSED
                                                                                            ? "CLOSED"
                                                                                            : finalState
                                                                                                            == Control
                                                                                                                    .BLOCKED
                                                                                                    ? "SUSPENDED"
                                                                                                    : "OPEN";
                                                                            return nativeRepository
                                                                                    .databaseTime()
                                                                                    .flatMap(
                                                                                            time ->
                                                                                                    requireOne(
                                                                                                                    enrollments
                                                                                                                            .updateAccountStatus(
                                                                                                                                    snapshot,
                                                                                                                                    status,
                                                                                                                                    finalState
                                                                                                                                                    == Control
                                                                                                                                                            .CLOSED
                                                                                                                                            ? time
                                                                                                                                                    .businessDate()
                                                                                                                                            : null),
                                                                                                                    "ACCOUNT_MONEY_CONTROL_CHANGED")
                                                                                                            .then(
                                                                                                                    enrollments
                                                                                                                            .insertStatusHistory(
                                                                                                                                    new FundsStatusHistoryInsert(
                                                                                                                                            UUID
                                                                                                                                                    .randomUUID(),
                                                                                                                                            account,
                                                                                                                                            FundsAccountStatus
                                                                                                                                                    .valueOf(
                                                                                                                                                            status),
                                                                                                                                            time
                                                                                                                                                    .businessTime(),
                                                                                                                                            "Managed"
                                                                                                                                                + " money"
                                                                                                                                                + " control"
                                                                                                                                                + " "
                                                                                                                                                    + input
                                                                                                                                                            .actionId())))
                                                                                                            .then(
                                                                                                                    requireOne(
                                                                                                                            enrollments
                                                                                                                                    .finishControl(
                                                                                                                                            current,
                                                                                                                                            input
                                                                                                                                                    .actionId(),
                                                                                                                                            finalState
                                                                                                                                                    .name()),
                                                                                                                            "ACCOUNT_MONEY_CONTROL_CHANGED"))
                                                                                                            .then(
                                                                                                                    requireOne(
                                                                                                                            enrollments
                                                                                                                                    .finishAction(
                                                                                                                                            action,
                                                                                                                                            receiptState),
                                                                                                                            "ACCOUNT_MONEY_CONTROL_CHANGED"))
                                                                                                            .then(
                                                                                                                    read(
                                                                                                                            tenant,
                                                                                                                            account,
                                                                                                                            false))
                                                                                                            .map(
                                                                                                                    this
                                                                                                                            ::toContract));
                                                                        })));
    }

    private void requireIntent(AccountMoneyAction action, UUID account, Manage input) {
        if (!account.equals(action.accountId())
                || !input.target().name().equals(action.target())
                || !input.expectedVersion().equals(action.expectedVersion()))
            throw fail(409, "ACCOUNT_CONTROL_INTENT_CONFLICT");
    }

    private Mono<Void> requireOne(Mono<Long> affected, String reason) {
        return affected.filter(count -> count == 1L)
                .switchIfEmpty(Mono.error(fail(409, reason)))
                .then();
    }

    private Mono<AccountMoneyEnrollment> read(String tenant, UUID account, boolean lock) {
        return (lock
                        ? enrollments.findLockedByTenantIdAndAccountId(tenant, account)
                        : enrollments.findByTenantIdAndAccountId(tenant, account))
                .switchIfEmpty(Mono.error(fail(404, "ENROLLMENT_NOT_FOUND")));
    }

    private Enrollment toContract(AccountMoneyEnrollment row) {
        return new Enrollment(
                row.accountId(),
                row.enrollmentId(),
                row.contractId(),
                row.currency(),
                Kind.valueOf(row.kind()),
                Mode.valueOf(row.mode()),
                Control.valueOf(row.control()),
                row.version());
    }

    static ResponseStatusException fail(int code, String reason) {
        return new ResponseStatusException(HttpStatus.valueOf(code), reason);
    }
}
