package io.fireflysoftware.core.banking.accounts.core.services.funds.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.core.services.funds.LedgerFundsPort;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;
import io.fireflysoftware.core.banking.accounts.models.repositories.funds.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class AccountFundsPersistenceServiceTest {
    private final AccountMoneyEnrollmentRepository enrollments =
            mock(AccountMoneyEnrollmentRepository.class);
    private final AccountMoneyActionRepository actions = mock(AccountMoneyActionRepository.class);
    private final FundsNativeRepository nativeRepository = mock(FundsNativeRepository.class);
    private final FundsDatabaseContext context = mock(FundsDatabaseContext.class);
    private final ReactiveTransactionManager manager = mock(ReactiveTransactionManager.class);
    private final SecurityPrincipal principal = mock(SecurityPrincipal.class);
    private final LedgerFundsPort ledger = mock(LedgerFundsPort.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<LedgerFundsPort> provider = mock(ObjectProvider.class);

    private final UUID account = UUID.randomUUID();
    private final UUID enrollment = UUID.randomUUID();
    private final UUID contract = UUID.randomUUID();
    private final UUID action = UUID.randomUUID();
    private final AtomicInteger active = new AtomicInteger();
    private AccountFundsServiceImpl service;
    private final Manage input = new Manage(action, Control.BLOCKED, 2L);

    private AccountMoneyEnrollment row(String mode, String control, long version, UUID pending) {
        return new AccountMoneyEnrollment(
                account,
                "tenant_a",
                enrollment,
                contract,
                "EUR",
                "CUSTOMER",
                mode,
                control,
                version,
                pending);
    }

    @BeforeEach
    void setup() {
        when(principal.subject()).thenReturn("actor");
        when(principal.tenantId()).thenReturn("tenant_a");
        when(principal.hasScope(anyString())).thenReturn(true);
        when(principal.claims())
                .thenReturn(
                        Map.of(
                                "exp",
                                Instant.now().plusSeconds(60),
                                "funds_accounts",
                                List.of(account.toString())));
        when(context.establish(eq(principal), eq("tenant_a"), anyString(), eq(account)))
                .thenReturn(Mono.empty());
        when(nativeRepository.lockAccount(account))
                .thenReturn(
                        Mono.just(
                                new FundsAccountSnapshot(
                                        account, contract, "EUR", FundsAccountStatus.OPEN, null)));
        when(provider.getIfAvailable(any())).thenReturn(ledger);
        when(manager.getReactiveTransaction(any()))
                .thenAnswer(
                        ignored ->
                                Mono.fromSupplier(
                                        () -> {
                                            assertThat(active.incrementAndGet()).isEqualTo(1);
                                            return mock(ReactiveTransaction.class);
                                        }));
        when(manager.commit(any()))
                .thenAnswer(
                        ignored ->
                                Mono.fromRunnable(
                                        () -> assertThat(active.decrementAndGet()).isZero()));
        when(manager.rollback(any()))
                .thenAnswer(
                        ignored ->
                                Mono.fromRunnable(
                                        () -> assertThat(active.decrementAndGet()).isZero()));
        service =
                new AccountFundsServiceImpl(
                        enrollments, actions, nativeRepository, manager, provider, context);
    }

    @Test
    void verifyUsesTheTenantReadWithoutAWriteLock() {
        when(enrollments.findByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(row("MANAGED", "ACTIVE", 2L, null)));
        assertThat(service.verify(principal, "tenant_a", account).block().version()).isEqualTo(2);
        verify(enrollments, never()).findLockedByTenantIdAndAccountId(any(), any());
    }

    @Test
    void linkReplayDoesNotRepeatTheUpdate() {
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(row("MANAGED", "ACTIVE", 2L, null)));
        assertThat(
                        service.link(principal, "tenant_a", account, new Link(enrollment))
                                .block()
                                .mode())
                .isEqualTo(Mode.MANAGED);
        verify(enrollments, never()).linkEnrollment(any());
    }

    @Test
    void zeroRowLinkCasCannotReturnSuccess() {
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(row("ENROLLING", "BLOCKED", 0L, null)));
        when(enrollments.linkEnrollment(any())).thenReturn(Mono.just(0L));
        when(enrollments.findByTenantIdAndAccountId("tenant_a", account)).thenReturn(Mono.empty());
        assertThatThrownBy(
                        () ->
                                service.link(principal, "tenant_a", account, new Link(enrollment))
                                        .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ENROLLMENT_LINK_CONFLICT");
    }

    @Test
    void controlPersistsPreparationThenCallsRemoteOutsideBothTransactionsAndUsesDatabaseTime() {
        var before = row("MANAGED", "ACTIVE", 2L, null);
        var pending = row("MANAGED", "BLOCKING", 3L, action);
        var after = row("MANAGED", "BLOCKED", 4L, null);
        var intent =
                new AccountMoneyAction(
                        new AccountMoneyActionId("tenant_a", action),
                        account,
                        "BLOCKED",
                        2L,
                        "PENDING",
                        "actor",
                        OffsetDateTime.now());
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(before), Mono.just(pending));
        when(actions.findByIdTenantIdAndIdActionId("tenant_a", action))
                .thenReturn(Mono.empty(), Mono.just(intent));
        when(nativeRepository.claimAction("tenant_a", action, account, "BLOCKED", 2L, "actor"))
                .thenReturn(Mono.just(1));
        when(enrollments.stageControl(before, action, "BLOCKING"))
                .thenReturn(
                        Mono.defer(
                                () -> {
                                    assertThat(active.get()).isEqualTo(1);
                                    return Mono.just(1L);
                                }));
        when(enrollments.findByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(pending), Mono.just(after));
        when(ledger.apply(eq("tenant_a"), any(), eq(action), eq(Control.BLOCKED)))
                .thenReturn(
                        Mono.defer(
                                () -> {
                                    assertThat(active.get()).isZero();
                                    return Mono.empty();
                                }));
        var time =
                new FundsDatabaseTime(LocalDate.of(2020, 2, 3), LocalDateTime.of(2020, 2, 3, 4, 5));
        when(nativeRepository.databaseTime()).thenReturn(Mono.just(time));
        when(enrollments.updateAccountStatus(any(), eq("SUSPENDED"), isNull()))
                .thenReturn(Mono.just(1L));
        when(enrollments.insertStatusHistory(any()))
                .thenAnswer(
                        call -> {
                            FundsStatusHistoryInsert history = call.getArgument(0);
                            assertThat(history.accountStatusHistoryId()).isNotNull();
                            assertThat(history.statusStartDatetime())
                                    .isEqualTo(time.businessTime());
                            assertThat(active.get()).isEqualTo(1);
                            return Mono.just(history);
                        });
        when(enrollments.finishControl(pending, action, "BLOCKED")).thenReturn(Mono.just(1L));
        when(enrollments.finishAction(intent, "COMPLETE")).thenReturn(Mono.just(1L));
        assertThat(service.control(principal, "tenant_a", account, input).block().control())
                .isEqualTo(Control.BLOCKED);
        assertThat(active.get()).isZero();
        verify(manager, times(2)).commit(any());
        verify(nativeRepository).databaseTime();
    }

    @Test
    void existingCompletedActionReplaysWithoutRemoteEffects() {
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(row("MANAGED", "BLOCKED", 4L, null)));
        when(actions.findByIdTenantIdAndIdActionId("tenant_a", action))
                .thenReturn(
                        Mono.just(
                                new AccountMoneyAction(
                                        new AccountMoneyActionId("tenant_a", action),
                                        account,
                                        "BLOCKED",
                                        2L,
                                        "COMPLETE",
                                        "actor",
                                        OffsetDateTime.now())));
        assertThat(service.control(principal, "tenant_a", account, input).block().control())
                .isEqualTo(Control.BLOCKED);
        verifyNoInteractions(ledger);
        verify(enrollments, never()).stageControl(any(), any(), any());
    }

    @Test
    void failedRemoteLeavesPreparedControlForRetryAndDoesNotFinishLocally() {
        var pending = row("MANAGED", "BLOCKING", 3L, action);
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(pending));
        when(actions.findByIdTenantIdAndIdActionId("tenant_a", action))
                .thenReturn(
                        Mono.just(
                                new AccountMoneyAction(
                                        new AccountMoneyActionId("tenant_a", action),
                                        account,
                                        "BLOCKED",
                                        2L,
                                        "PENDING",
                                        "actor",
                                        OffsetDateTime.now())));
        when(ledger.apply(eq("tenant_a"), any(), eq(action), eq(Control.BLOCKED)))
                .thenReturn(Mono.error(new IllegalStateException("owned remote unavailable")));
        assertThatThrownBy(() -> service.control(principal, "tenant_a", account, input).block())
                .hasMessageContaining("owned remote unavailable");
        verify(manager).commit(any());
        verify(enrollments, never()).finishControl(any(), any(), any());
        verify(nativeRepository, never()).databaseTime();
    }

    @Test
    void expiredPolicyAfterRemoteCannotEnterTheCompletionTransaction() {
        var pending = row("MANAGED", "BLOCKING", 3L, action);
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(pending));
        when(actions.findByIdTenantIdAndIdActionId("tenant_a", action))
                .thenReturn(
                        Mono.just(
                                new AccountMoneyAction(
                                        new AccountMoneyActionId("tenant_a", action),
                                        account,
                                        "BLOCKED",
                                        2L,
                                        "PENDING",
                                        "actor",
                                        OffsetDateTime.now())));
        when(ledger.apply(eq("tenant_a"), any(), eq(action), eq(Control.BLOCKED)))
                .thenReturn(
                        Mono.fromRunnable(
                                () ->
                                        when(principal.claims())
                                                .thenReturn(
                                                        Map.of(
                                                                "exp",
                                                                Instant.now().minusSeconds(1),
                                                                "funds_accounts",
                                                                List.of(account.toString())))));
        assertThatThrownBy(() -> service.control(principal, "tenant_a", account, input).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FINITE_FUNDS_TOKEN_REQUIRED");
        verify(manager).getReactiveTransaction(any());
        verify(nativeRepository, never()).databaseTime();
        verify(enrollments, never()).finishControl(any(), any(), any());
    }

    @Test
    void conflictingActionIntentRollsBackBeforeCallingRemote() {
        when(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                .thenReturn(Mono.just(row("MANAGED", "ACTIVE", 2L, null)));
        when(actions.findByIdTenantIdAndIdActionId("tenant_a", action))
                .thenReturn(
                        Mono.just(
                                new AccountMoneyAction(
                                        new AccountMoneyActionId("tenant_a", action),
                                        UUID.randomUUID(),
                                        "BLOCKED",
                                        2L,
                                        "PENDING",
                                        "actor",
                                        OffsetDateTime.now())));
        assertThatThrownBy(() -> service.control(principal, "tenant_a", account, input).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ACCOUNT_CONTROL_INTENT_CONFLICT");
        verify(manager).rollback(any());
        verifyNoInteractions(ledger);
        verify(enrollments, never()).stageControl(any(), any(), any());
    }
}
