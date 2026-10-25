package io.fireflysoftware.core.banking.accounts.core.services.funds.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.core.services.funds.AccountFundsService;
import io.fireflysoftware.core.banking.accounts.core.services.funds.LedgerFundsPort;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;
import io.fireflysoftware.core.banking.accounts.models.entities.funds.*;
import io.fireflysoftware.core.banking.accounts.models.repositories.funds.*;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.*;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class AccountsFundsPersistenceNativeIT {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final List<Execution> EXECUTED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static AnnotationConfigApplicationContext application;
    private AccountFundsService service;
    private AccountMoneyEnrollmentRepository enrollments;
    private AccountMoneyActionRepository actions;
    private FundsNativeRepository nativeRepository;
    private FundsDatabaseContext context;
    private TransactionalOperator tx;
    private RecordingLedger ledger;
    private SecurityPrincipal principal;
    private UUID account;
    private UUID contract;
    private UUID enrollment;

    private record Execution(int connection, String operation) {}

    static class RecordingLedger implements LedgerFundsPort {
        int calls;
        Function<Control, Mono<Void>> response = ignored -> Mono.empty();

        public Mono<Void> apply(String tenant, Enrollment current, UUID actionId, Control target) {
            return Mono.defer(
                    () ->
                            TransactionSynchronizationManager.forCurrentTransaction()
                                    .map(
                                            TransactionSynchronizationManager
                                                    ::isActualTransactionActive)
                                    .onErrorReturn(NoTransactionException.class, false)
                                    .flatMap(
                                            active -> {
                                                assertThat(active)
                                                        .as("remote port outside local transaction")
                                                        .isFalse();
                                                calls++;
                                                return response.apply(target);
                                            }));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("accounts-funds-native-fixture")
    @EnableTransactionManagement
    @EnableR2dbcRepositories(basePackageClasses = AccountMoneyEnrollmentRepository.class)
    @Import({AccountFundsServiceImpl.class, FundsDatabaseContext.class})
    static class Database
            extends io.fireflysoftware.core.banking.accounts.models.config.R2dbcConfiguration {
        @Bean
        public ConnectionFactory connectionFactory() {
            var delegate =
                    new PostgresqlConnectionFactory(
                            PostgresqlConnectionConfiguration.builder()
                                    .host("127.0.0.1")
                                    .port(
                                            Integer.parseInt(
                                                    System.getProperty("accounts.native.port")))
                                    .database("postgres")
                                    .username("accounts_funds_runtime")
                                    .password(System.getenv("ACCOUNTS_NATIVE_PASSWORD"))
                                    .build());
            return new ConnectionFactory() {
                public Publisher<? extends Connection> create() {
                    return Flux.from(delegate.create())
                            .map(
                                    connection -> {
                                        int id = CONNECTIONS.incrementAndGet();
                                        return (Connection)
                                                Proxy.newProxyInstance(
                                                        Connection.class.getClassLoader(),
                                                        new Class<?>[] {Connection.class},
                                                        (proxy, method, args) -> {
                                                            if (method.getName()
                                                                    .equals("createStatement"))
                                                                EXECUTED.add(
                                                                        new Execution(
                                                                                id,
                                                                                (String) args[0]));
                                                            if (Set.of(
                                                                            "beginTransaction",
                                                                            "commitTransaction",
                                                                            "rollbackTransaction")
                                                                    .contains(method.getName()))
                                                                EXECUTED.add(
                                                                        new Execution(
                                                                                id,
                                                                                method.getName()));
                                                            try {
                                                                return method.invoke(
                                                                        connection, args);
                                                            } catch (
                                                                    InvocationTargetException
                                                                            error) {
                                                                throw error.getCause();
                                                            }
                                                        });
                                    });
                }

                public ConnectionFactoryMetadata getMetadata() {
                    return delegate.getMetadata();
                }
            };
        }

        @Bean
        ReactiveTransactionManager transactionManager(ConnectionFactory factory) {
            return new R2dbcTransactionManager(factory);
        }

        @Bean
        RecordingLedger recordingLedger() {
            return new RecordingLedger();
        }
    }

    @BeforeAll
    static void startSpring() {
        application = new AnnotationConfigApplicationContext();
        application.getEnvironment().setActiveProfiles("accounts-funds-native-fixture");
        application.register(Database.class);
        application.refresh();
    }

    @AfterAll
    static void stopSpring() {
        if (application != null) application.close();
    }

    @BeforeEach
    void freshUnmanagedAccountFixture() throws Exception {
        account = UUID.randomUUID();
        contract = UUID.randomUUID();
        enrollment = UUID.randomUUID();
        service = application.getBean(AccountFundsService.class);
        enrollments = application.getBean(AccountMoneyEnrollmentRepository.class);
        actions = application.getBean(AccountMoneyActionRepository.class);
        nativeRepository = application.getBean(FundsNativeRepository.class);
        context = application.getBean(FundsDatabaseContext.class);
        tx = TransactionalOperator.create(application.getBean(ReactiveTransactionManager.class));
        ledger = application.getBean(RecordingLedger.class);
        ledger.calls = 0;
        ledger.response = ignored -> Mono.empty();
        principal = mock(SecurityPrincipal.class);
        when(principal.subject()).thenReturn("owned_native_actor");
        when(principal.tenantId()).thenReturn("tenant_a");
        when(principal.hasScope(anyString())).thenReturn(true);
        when(principal.claims()).thenReturn(claims(Instant.now().plusSeconds(120)));
        try (var connection = jdbc();
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id)"
                                    + " VALUES(?,?,?,'CHECKING','EUR',CURRENT_DATE,'OPEN',?)")) {
            statement.setObject(1, account);
            statement.setObject(2, contract);
            statement.setString(3, "owned-native-" + account);
            statement.setObject(4, UUID.randomUUID());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        EXECUTED.clear();
    }

    private Map<String, Object> claims(Instant expiry) {
        return Map.of(
                "exp",
                expiry,
                "funds_accounts",
                List.of(account.toString()),
                "funds_enrollments",
                List.of(
                        Map.of(
                                "tenantId",
                                "tenant_a",
                                "accountId",
                                account.toString(),
                                "contractId",
                                contract.toString(),
                                "currency",
                                "EUR",
                                "kind",
                                "CUSTOMER")));
    }

    private java.sql.Connection jdbc() throws Exception {
        return DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:"
                        + System.getProperty("accounts.native.port")
                        + "/postgres",
                "accounts_funds_runtime",
                System.getenv("ACCOUNTS_NATIVE_PASSWORD"));
    }

    private <T> T scoped(Mono<T> command) {
        return context.establish(principal, "tenant_a", "CONTROL", account)
                .then(command)
                .as(tx::transactional)
                .block(TIMEOUT);
    }

    private Enrollment managed() {
        var first =
                service.begin(principal, "tenant_a", account, new Begin(enrollment, Kind.CUSTOMER))
                        .block(TIMEOUT);
        assertThat(first.mode()).isEqualTo(Mode.ENROLLING);
        return service.link(principal, "tenant_a", account, new Link(enrollment)).block(TIMEOUT);
    }

    private List<Map<String, Object>> rows(String sql) throws Exception {
        try (var connection = jdbc()) {
            connection.setAutoCommit(false);
            try (var scope =
                    connection.prepareStatement(
                            "SELECT"
                                + " set_config('firefly.funds.tenant','tenant_a',true),set_config('firefly.funds.accounts',?,true),set_config('firefly.funds.purpose','VERIFY',true)")) {
                scope.setString(1, "[\"" + account + "\"]");
                scope.execute();
            }
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, account);
                try (var result = statement.executeQuery()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (result.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= result.getMetaData().getColumnCount(); i++)
                            row.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                        rows.add(row);
                    }
                    connection.rollback();
                    return rows;
                }
            }
        }
    }

    @Test
    void freshAdmissionLinkReplayAndScopedVisibilityUseRealRepositories() throws Exception {
        var current = managed();
        assertThat(current.control()).isEqualTo(Control.ACTIVE);
        assertThat(current.version()).isEqualTo(1);
        assertThat(
                        service.begin(
                                        principal,
                                        "tenant_a",
                                        account,
                                        new Begin(enrollment, Kind.CUSTOMER))
                                .block(TIMEOUT))
                .isEqualTo(current);
        assertThat(
                        service.link(principal, "tenant_a", account, new Link(enrollment))
                                .block(TIMEOUT))
                .isEqualTo(current);
        assertThat(service.verify(principal, "tenant_a", account).block(TIMEOUT))
                .isEqualTo(current);
        assertThat(enrollments.findByTenantIdAndAccountId("tenant_a", account).block(TIMEOUT))
                .isNull();
        assertThat(
                        context.establish(principal, "tenant_a", "VERIFY", account)
                                .then(
                                        enrollments.findByTenantIdAndAccountId(
                                                "other_tenant", account))
                                .as(tx::transactional)
                                .block(TIMEOUT))
                .isNull();
        assertThat(
                        rows(
                                "SELECT mode,control,version FROM account_money_enrollment WHERE"
                                        + " account_id=?"))
                .hasSize(1);
        assertThat(EXECUTED.stream().map(Execution::operation))
                .anyMatch(s -> s.contains("FOR NO KEY UPDATE"));
        assertThat(EXECUTED.stream().map(Execution::operation))
                .anyMatch(s -> s.contains("FOR UPDATE"));
    }

    @Test
    void existingLegacyBalanceMakesAtomicFreshAdmissionUnavailable() throws Exception {
        try (var connection = jdbc();
                var statement =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " account_balance(account_id,balance_type,balance_amount,as_of_datetime)"
                                    + " VALUES(?,'CURRENT',0,CURRENT_TIMESTAMP)")) {
            statement.setObject(1, account);
            statement.executeUpdate();
        }
        assertThatThrownBy(
                        () ->
                                service.begin(
                                                principal,
                                                "tenant_a",
                                                account,
                                                new Begin(enrollment, Kind.CUSTOMER))
                                        .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FRESH_OPEN_ACCOUNT_REQUIRED");
        assertThat(rows("SELECT account_id FROM account_money_enrollment WHERE account_id=?"))
                .isEmpty();
    }

    @Test
    void controlUsesRealCompositeActionHistoryAndOneConnectionPerLocalTransaction()
            throws Exception {
        var initial = managed();
        UUID action = UUID.randomUUID();
        EXECUTED.clear();
        var result =
                service.control(
                                principal,
                                "tenant_a",
                                account,
                                new Manage(action, Control.BLOCKED, initial.version()))
                        .block(TIMEOUT);
        assertThat(result.control()).isEqualTo(Control.BLOCKED);
        assertThat(result.version()).isEqualTo(3);
        assertThat(ledger.calls).isEqualTo(1);
        var intent = scoped(actions.findByIdTenantIdAndIdActionId("tenant_a", action));
        assertThat(intent.id()).isEqualTo(new AccountMoneyActionId("tenant_a", action));
        assertThat(intent.state()).isEqualTo("COMPLETE");
        assertThat(intent.actor()).isEqualTo("owned_native_actor");
        assertThat(intent.createdAt()).isNotNull();
        var history =
                rows(
                        "SELECT"
                            + " account_status_history_id,status_code::text,status_start_datetime,date_created,date_updated"
                            + " FROM account_status_history WHERE account_id=?");
        assertThat(history).hasSize(1);
        var row = history.getFirst();
        assertThat(row.get("account_status_history_id")).isNotNull();
        assertThat(row.get("status_code")).isEqualTo("SUSPENDED");
        assertThat(row.get("status_start_datetime"))
                .isEqualTo(row.get("date_created"))
                .isEqualTo(row.get("date_updated"));
        var commands =
                EXECUTED.stream()
                        .filter(e -> e.operation().contains("set_config('firefly.funds.tenant'"))
                        .toList();
        assertThat(commands).hasSize(3);
        for (var command : commands) {
            var local =
                    EXECUTED.stream()
                            .filter(e -> e.connection() == command.connection())
                            .map(Execution::operation)
                            .toList();
            assertThat(local).contains("beginTransaction", "commitTransaction");
            assertThat(local).anyMatch(sql -> sql.contains("pg_catalog.pg_roles"));
        }
        assertThat(
                        service.control(
                                        principal,
                                        "tenant_a",
                                        account,
                                        new Manage(action, Control.BLOCKED, initial.version()))
                                .block(TIMEOUT))
                .isEqualTo(result);
        assertThat(ledger.calls).isEqualTo(1);
        assertThat(
                        rows(
                                "SELECT account_status_history_id FROM account_status_history WHERE"
                                        + " account_id=?"))
                .hasSize(1);
    }

    @Test
    void remoteFailureRetainsDurablePendingControlAndRetryCompletes() throws Exception {
        var initial = managed();
        UUID action = UUID.randomUUID();
        ledger.response =
                ignored ->
                        Mono.error(new IllegalStateException("owned mock transport unavailable"));
        var input = new Manage(action, Control.BLOCKED, initial.version());
        assertThatThrownBy(
                        () -> service.control(principal, "tenant_a", account, input).block(TIMEOUT))
                .hasMessageContaining("owned mock transport unavailable");
        assertThat(service.verify(principal, "tenant_a", account).block(TIMEOUT).control())
                .isEqualTo(Control.BLOCKING);
        assertThat(scoped(actions.findByIdTenantIdAndIdActionId("tenant_a", action)).state())
                .isEqualTo("PENDING");
        assertThat(
                        rows(
                                "SELECT account_status_history_id FROM account_status_history WHERE"
                                        + " account_id=?"))
                .isEmpty();
        ledger.response = ignored -> Mono.empty();
        assertThat(service.control(principal, "tenant_a", account, input).block(TIMEOUT).control())
                .isEqualTo(Control.BLOCKED);
        assertThat(ledger.calls).isEqualTo(2);
    }

    @Test
    void rejectedClosureRecordsBlockedRejectedAndReplaysWithoutRemote() throws Exception {
        var initial = managed();
        UUID action = UUID.randomUUID();
        ledger.response = ignored -> Mono.error(new LedgerFundsPort.ClosureRejected());
        var input = new Manage(action, Control.CLOSED, initial.version());
        assertThatThrownBy(
                        () -> service.control(principal, "tenant_a", account, input).block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ACCOUNT_CLOSURE_REJECTED_WITH_FUNDS");
        assertThat(service.verify(principal, "tenant_a", account).block(TIMEOUT).control())
                .isEqualTo(Control.BLOCKED);
        assertThat(scoped(actions.findByIdTenantIdAndIdActionId("tenant_a", action)).state())
                .isEqualTo("REJECTED");
        assertThat(
                        rows("SELECT account_status::text,close_date FROM account WHERE"
                                        + " account_id=?")
                                .getFirst())
                .containsEntry("account_status", "SUSPENDED")
                .containsEntry("close_date", null);
        assertThatThrownBy(
                        () -> service.control(principal, "tenant_a", account, input).block(TIMEOUT))
                .hasMessageContaining("ACCOUNT_CLOSURE_REJECTED_WITH_FUNDS");
        assertThat(ledger.calls).isEqualTo(1);
    }

    @Test
    void expiryAfterRemoteRetainsPendingStateWithoutLocalCompletion() throws Exception {
        var initial = managed();
        UUID action = UUID.randomUUID();
        ledger.response =
                ignored ->
                        Mono.fromRunnable(
                                () ->
                                        when(principal.claims())
                                                .thenReturn(claims(Instant.now().minusSeconds(1))));
        assertThatThrownBy(
                        () ->
                                service.control(
                                                principal,
                                                "tenant_a",
                                                account,
                                                new Manage(
                                                        action, Control.BLOCKED, initial.version()))
                                        .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("FINITE_FUNDS_TOKEN_REQUIRED");
        assertThat(
                        rows("SELECT control,pending_action_id FROM account_money_enrollment WHERE"
                                        + " account_id=?")
                                .getFirst())
                .containsEntry("control", "BLOCKING")
                .containsEntry("pending_action_id", action);
        assertThat(rows("SELECT state FROM account_money_action WHERE account_id=?").getFirst())
                .containsEntry("state", "PENDING");
        assertThat(
                        rows(
                                "SELECT account_status_history_id FROM account_status_history WHERE"
                                        + " account_id=?"))
                .isEmpty();
    }

    @Test
    void staleCasReturnsZeroAndRollbackRemovesEarlierWritesInTheSameTransaction() throws Exception {
        managed();
        UUID action = UUID.randomUUID();
        Mono<Void> command =
                nativeRepository
                        .lockAccount(account)
                        .then(enrollments.findLockedByTenantIdAndAccountId("tenant_a", account))
                        .flatMap(
                                expected ->
                                        nativeRepository
                                                .claimAction(
                                                        "tenant_a",
                                                        action,
                                                        account,
                                                        "BLOCKED",
                                                        expected.version(),
                                                        "owned_native_actor")
                                                .then(
                                                        enrollments.stageControl(
                                                                expected, action, "BLOCKING"))
                                                .doOnNext(count -> assertThat(count).isEqualTo(1))
                                                .then(
                                                        enrollments.stageControl(
                                                                expected, action, "BLOCKING"))
                                                .flatMap(
                                                        count -> {
                                                            assertThat(count).isZero();
                                                            return Mono.error(
                                                                    new IllegalStateException(
                                                                            "owned CAS rollback"
                                                                                    + " control"));
                                                        }));
        assertThatThrownBy(() -> scoped(command))
                .hasMessageContaining("owned CAS rollback control");
        assertThat(service.verify(principal, "tenant_a", account).block(TIMEOUT).control())
                .isEqualTo(Control.ACTIVE);
        assertThat(rows("SELECT action_id FROM account_money_action WHERE account_id=?")).isEmpty();
    }

    @Test
    void duplicateAssignedHistoryIdFailsAndRollsBackAccountEnrollmentActionAndHistory()
            throws Exception {
        managed();
        UUID action = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        Mono<Void> command =
                nativeRepository
                        .lockAccount(account)
                        .flatMap(
                                snapshot ->
                                        enrollments
                                                .findLockedByTenantIdAndAccountId(
                                                        "tenant_a", account)
                                                .flatMap(
                                                        expected ->
                                                                nativeRepository
                                                                        .claimAction(
                                                                                "tenant_a",
                                                                                action,
                                                                                account,
                                                                                "BLOCKED",
                                                                                expected.version(),
                                                                                "owned_native_actor")
                                                                        .then(
                                                                                enrollments
                                                                                        .stageControl(
                                                                                                expected,
                                                                                                action,
                                                                                                "BLOCKING"))
                                                                        .then(
                                                                                nativeRepository
                                                                                        .databaseTime())
                                                                        .flatMap(
                                                                                time -> {
                                                                                    var history =
                                                                                            new FundsStatusHistoryInsert(
                                                                                                    historyId,
                                                                                                    account,
                                                                                                    FundsAccountStatus
                                                                                                            .SUSPENDED,
                                                                                                    time
                                                                                                            .businessTime(),
                                                                                                    "owned"
                                                                                                        + " rollback"
                                                                                                        + " fixture");
                                                                                    return enrollments
                                                                                            .updateAccountStatus(
                                                                                                    snapshot,
                                                                                                    "SUSPENDED",
                                                                                                    null)
                                                                                            .then(
                                                                                                    enrollments
                                                                                                            .insertStatusHistory(
                                                                                                                    history))
                                                                                            .then(
                                                                                                    enrollments
                                                                                                            .insertStatusHistory(
                                                                                                                    history))
                                                                                            .then();
                                                                                })));
        assertThatThrownBy(() -> scoped(command)).hasMessageContaining("duplicate key");
        assertThat(service.verify(principal, "tenant_a", account).block(TIMEOUT).control())
                .isEqualTo(Control.ACTIVE);
        assertThat(rows("SELECT account_status::text FROM account WHERE account_id=?").getFirst())
                .containsEntry("account_status", "OPEN");
        assertThat(rows("SELECT action_id FROM account_money_action WHERE account_id=?")).isEmpty();
        assertThat(
                        rows(
                                "SELECT account_status_history_id FROM account_status_history WHERE"
                                        + " account_id=?"))
                .isEmpty();
    }

    @Test
    void closeDateAndEffectiveHistoryTimeComeFromTheActualCompletionTransaction() throws Exception {
        var initial = managed();
        assertThat(
                        service.control(
                                        principal,
                                        "tenant_a",
                                        account,
                                        new Manage(
                                                UUID.randomUUID(),
                                                Control.CLOSED,
                                                initial.version()))
                                .block(TIMEOUT)
                                .control())
                .isEqualTo(Control.CLOSED);
        var history =
                rows("SELECT h.status_start_datetime,h.date_created,h.date_updated,a.close_date"
                         + " FROM account_status_history h JOIN account a USING(account_id) WHERE"
                         + " h.account_id=?")
                        .getFirst();
        var effect = ((java.sql.Timestamp) history.get("status_start_datetime")).toLocalDateTime();
        assertThat(history.get("date_created")).isEqualTo(history.get("status_start_datetime"));
        assertThat(history.get("date_updated")).isEqualTo(history.get("status_start_datetime"));
        assertThat(((java.sql.Date) history.get("close_date")).toLocalDate())
                .isEqualTo(effect.toLocalDate());
        var pair =
                scoped(
                        nativeRepository
                                .databaseTime()
                                .flatMap(
                                        first ->
                                                Mono.delay(Duration.ofMillis(20))
                                                        .then(nativeRepository.databaseTime())
                                                        .map(second -> List.of(first, second))));
        assertThat(pair.getFirst()).isEqualTo(pair.getLast());
    }

    @Test
    void nativeAccountLockRetainsNoKeyUpdateCompatibility() {
        var locked =
                scoped(
                        nativeRepository
                                .lockAccount(account)
                                .flatMap(
                                        snapshot ->
                                                Mono.fromCallable(
                                                        () -> {
                                                            try (var connection = jdbc()) {
                                                                connection.setAutoCommit(false);
                                                                try (var statement =
                                                                        connection.prepareStatement(
                                                                                "SELECT account_id"
                                                                                    + " FROM"
                                                                                    + " account"
                                                                                    + " WHERE"
                                                                                    + " account_id=?"
                                                                                    + " FOR KEY"
                                                                                    + " SHARE"
                                                                                    + " NOWAIT")) {
                                                                    statement.setObject(1, account);
                                                                    try (var result =
                                                                            statement
                                                                                    .executeQuery()) {
                                                                        assertThat(result.next())
                                                                                .isTrue();
                                                                    }
                                                                    connection.rollback();
                                                                }
                                                            }
                                                            return snapshot;
                                                        })));
        assertThat(locked.accountId()).isEqualTo(account);
    }
}
