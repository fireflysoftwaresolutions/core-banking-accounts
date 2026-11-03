package io.fireflysoftware.core.banking.accounts.models.repositories;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;
import io.fireflysoftware.core.banking.accounts.models.repositories.funds.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.*;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;

import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;

class AccountsFundsPersistenceTest {
    private final R2dbcEntityOperations operations = mock(R2dbcEntityOperations.class);
    private final FundsPersistenceImpl persistence = new FundsPersistenceImpl(operations);
    private final UUID account = UUID.randomUUID();
    private final UUID enrollment = UUID.randomUUID();
    private final UUID action = UUID.randomUUID();
    private final DefaultReactiveDataAccessStrategy strategy =
            new DefaultReactiveDataAccessStrategy(
                    PostgresDialect.INSTANCE,
                    io.fireflysoftware.core.banking.accounts.models.config
                            .AccountsStatusR2dbcConverters.converters());
    private String sql;
    private final List<Object> binds = new ArrayList<>();

    private AccountMoneyEnrollment expected(UUID pending) {
        return new AccountMoneyEnrollment(
                account,
                "tenant_a",
                enrollment,
                UUID.randomUUID(),
                "EUR",
                "CUSTOMER",
                "MANAGED",
                "BLOCKING",
                7L,
                pending);
    }

    private void captureUpdate(long count) {
        when(operations.update(any(Query.class), any(Update.class), any(Class.class)))
                .thenAnswer(
                        call -> {
                            Query query = call.getArgument(0);
                            Update update = call.getArgument(1);
                            Class<?> type = call.getArgument(2);
                            var mapper = strategy.getStatementMapper().forType(type);
                            var prepared =
                                    mapper.getMappedObject(
                                            mapper.createUpdate(strategy.getTableName(type), update)
                                                    .withCriteria(
                                                            query.getCriteria().orElseThrow()));
                            sql = prepared.toQuery().replace("\"", "");
                            prepared.bindTo(
                                    new org.springframework.r2dbc.core.binding.BindTarget() {
                                        public void bind(String identifier, Object value) {
                                            binds.add(
                                                    value
                                                                    instanceof
                                                                    io.r2dbc.spi.Parameter parameter
                                                            ? parameter.getValue()
                                                            : value);
                                        }

                                        public void bind(int index, Object value) {
                                            binds.add(
                                                    value
                                                                    instanceof
                                                                    io.r2dbc.spi.Parameter parameter
                                                            ? parameter.getValue()
                                                            : value);
                                        }

                                        public void bindNull(String identifier, Class<?> type) {
                                            binds.add(null);
                                        }

                                        public void bindNull(int index, Class<?> type) {
                                            binds.add(null);
                                        }
                                    });
                            return Mono.just(count);
                        });
    }

    @Test
    void enrollmentUpdateBindsTenantIdentityVersionStateAndPendingPointer() {
        captureUpdate(1);
        assertThat(persistence.stageControl(expected(null), action, "BLOCKING").block())
                .isEqualTo(1L);
        assertThat(sql)
                .contains(
                        "tenant_id",
                        "account_id",
                        "enrollment_id",
                        "mode",
                        "control",
                        "version",
                        "pending_action_id IS NULL");
        assertThat(binds)
                .contains("tenant_a", account, enrollment, "MANAGED", "BLOCKING", 7L, 8L, action);
    }

    @Test
    void finishRequiresTheExactPendingActionAndPreservesZeroRowResult() {
        assertThat(
                        persistence
                                .finishControl(expected(UUID.randomUUID()), action, "BLOCKED")
                                .block())
                .isZero();
        verifyNoInteractions(operations);
        captureUpdate(0);
        assertThat(persistence.finishControl(expected(action), action, "BLOCKED").block()).isZero();
        assertThat(sql).contains("pending_action_id =", "tenant_id", "version");
        assertThat(binds).contains(action, 7L, 8L);
    }

    @Test
    void actionUpdateKeepsCompositeIdentityAndIntentAndPendingState() {
        captureUpdate(1);
        var expected =
                new AccountMoneyAction(
                        new AccountMoneyActionId("tenant_a", action),
                        account,
                        "BLOCKED",
                        6L,
                        "PENDING",
                        "actor",
                        OffsetDateTime.now());
        assertThat(persistence.finishAction(expected, "COMPLETE").block()).isEqualTo(1L);
        assertThat(sql)
                .contains(
                        "tenant_id",
                        "action_id",
                        "account_id",
                        "target",
                        "expected_version",
                        "state");
        assertThat(binds)
                .contains("tenant_a", action, account, "BLOCKED", 6L, "PENDING", "COMPLETE");
    }

    @Test
    void accountStatusCasRetainsCloseDateExceptForExplicitDatabaseDate() {
        captureUpdate(1);
        var snapshot =
                new FundsAccountSnapshot(
                        account, UUID.randomUUID(), "EUR", FundsAccountStatus.SUSPENDED, null);
        persistence.updateAccountStatus(snapshot, "OPEN", null).block();
        assertThat(sql).doesNotContain("close_date").contains("account_status", "account_id");
        assertThat(binds).contains(account, "OPEN", "SUSPENDED");
        persistence.updateAccountStatus(snapshot, "CLOSED", LocalDate.of(2026, 1, 2)).block();
        assertThat(sql).contains("close_date");
        assertThat(binds).contains(LocalDate.of(2026, 1, 2));
    }

    @Test
    void compositeActionAndAssignedHistoryIdsMapToRealColumnsWithoutTechnicalAuditValues() {
        var id = new AccountMoneyActionId("tenant_a", action);
        var row =
                strategy.getOutboundRow(
                        new AccountMoneyAction(
                                id, account, "ACTIVE", 1L, "PENDING", "actor", null));
        assertThat(row.keySet().stream().map(k -> k.getReference()))
                .contains("tenant_id", "action_id");
        var history =
                new FundsStatusHistoryInsert(
                        UUID.randomUUID(),
                        account,
                        FundsAccountStatus.OPEN,
                        LocalDateTime.of(2026, 1, 1, 0, 0),
                        "Managed control");
        var historyRow = strategy.getOutboundRow(history);
        assertThat(historyRow.keySet().stream().map(k -> k.getReference()))
                .contains("account_status_history_id", "status_start_datetime")
                .doesNotContain("date_created", "date_updated");
        when(operations.insert(history)).thenReturn(Mono.just(history));
        assertThat(persistence.insertStatusHistory(history).block()).isSameAs(history);
        verify(operations).insert(history);
        assertThatThrownBy(
                        () ->
                                persistence
                                        .insertStatusHistory(
                                                new FundsStatusHistoryInsert(
                                                        null,
                                                        account,
                                                        FundsAccountStatus.OPEN,
                                                        history.statusStartDatetime(),
                                                        "reason"))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authorityRepositoriesExposeNeitherGenericSaveNorDelete() {
        for (var type :
                List.of(
                        AccountMoneyEnrollmentRepository.class,
                        AccountMoneyActionRepository.class,
                        FundsNativeRepository.class)) {
            assertThat(Arrays.stream(type.getMethods()).map(m -> m.getName()))
                    .noneMatch(name -> name.equals("save") || name.startsWith("delete"));
        }
    }

    @Test
    void
            genuineRepositoryFactoryValidatesCompositeDerivedPathsAndNativeContractsWithoutConnecting() {
        var client = mock(org.springframework.r2dbc.core.DatabaseClient.class);
        when(operations.getDatabaseClient()).thenReturn(client);
        when(operations.getDataAccessStrategy()).thenReturn(strategy);
        when(operations.getConverter()).thenReturn(strategy.getConverter());
        var factory =
                new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(
                        operations);
        assertThat(factory.getRepository(AccountMoneyActionRepository.class)).isNotNull();
        assertThat(factory.getRepository(FundsNativeRepository.class)).isNotNull();
        assertThat(
                        factory.getRepository(
                                AccountMoneyEnrollmentRepository.class,
                                org.springframework.data.repository.core.support
                                        .RepositoryComposition.RepositoryFragments.just(
                                        persistence)))
                .isNotNull();
        verifyNoInteractions(client);
    }
}
