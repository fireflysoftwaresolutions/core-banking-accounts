package io.fireflysoftware.core.banking.accounts.models.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.AccountSpaceRepository;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.SpaceTransactionRepository;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

class AccountsOrdinaryRepositoryNativeIT {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID ZERO_ACCOUNT =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 1, 2, 0, 0);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final List<String> EXECUTED = new CopyOnWriteArrayList<>();

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(
            basePackageClasses = {AccountSpaceRepository.class, AssetPriceRepository.class})
    static class Database extends AbstractR2dbcConfiguration {
        @Override
        public ConnectionFactory connectionFactory() {
            var delegate =
                    new PostgresqlConnectionFactory(
                            PostgresqlConnectionConfiguration.builder()
                                    .host("127.0.0.1")
                                    .port(
                                            Integer.parseInt(
                                                    System.getProperty("accounts.native.port")))
                                    .database("postgres")
                                    .username("accounts_queries_runtime")
                                    .password(System.getenv("ACCOUNTS_NATIVE_PASSWORD"))
                                    .build());
            return new ConnectionFactory() {
                @Override
                public Publisher<? extends Connection> create() {
                    return Flux.from(delegate.create())
                            .map(
                                    connection ->
                                            (Connection)
                                                    Proxy.newProxyInstance(
                                                            Connection.class.getClassLoader(),
                                                            new Class<?>[] {Connection.class},
                                                            (proxy, method, args) -> {
                                                                if (method.getName()
                                                                        .equals("createStatement"))
                                                                    EXECUTED.add((String) args[0]);
                                                                try {
                                                                    return method.invoke(
                                                                            connection, args);
                                                                } catch (
                                                                        InvocationTargetException
                                                                                error) {
                                                                    throw error.getCause();
                                                                }
                                                            }));
                }

                @Override
                public ConnectionFactoryMetadata getMetadata() {
                    return delegate.getMetadata();
                }
            };
        }
    }

    @BeforeEach
    void resetPriceMetadata() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                "jdbc:postgresql://127.0.0.1:"
                                        + System.getProperty("accounts.native.port")
                                        + "/postgres",
                                "accounts_queries_runtime",
                                System.getenv("ACCOUNTS_NATIVE_PASSWORD"));
                var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM asset_price");
            statement.executeUpdate(
                    """
                    INSERT INTO asset_price(asset_price_id,asset_symbol,quote_currency,price,as_of_datetime,price_source,created_by,created_date) VALUES
                    ('00000000-0000-0000-0000-000000000101','BTC','USD',11,'2026-01-01','owned-query-fixture','owned-query-fixture',CURRENT_TIMESTAMP),
                    ('00000000-0000-0000-0000-000000000102','BTC','USD',12,'2026-01-02','owned-query-fixture','owned-query-fixture',CURRENT_TIMESTAMP),
                    ('00000000-0000-0000-0000-000000000103','BTC','USD',13,'2026-01-03','owned-query-fixture','owned-query-fixture',CURRENT_TIMESTAMP),
                    ('00000000-0000-0000-0000-000000000104','ETH','USD',21,'2026-01-04','owned-query-fixture','owned-query-fixture',CURRENT_TIMESTAMP),
                    ('00000000-0000-0000-0000-000000000105','BTC','EUR',14,'2026-01-05','owned-query-fixture','owned-query-fixture',CURRENT_TIMESTAMP)
                    """);
        }
        EXECUTED.clear();
    }

    private AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(Database.class);
    }

    @Test
    void balanceSumKeepsScaleFourZeroAndEmptyDistinct() {
        try (var context = context()) {
            var repository = context.getBean(AccountSpaceRepository.class);
            assertThat(repository.calculateTotalBalance(ACCOUNT).block(TIMEOUT))
                    .isEqualByComparingTo("12.3456");
            assertThat(repository.calculateTotalBalance(ZERO_ACCOUNT).block(TIMEOUT))
                    .isEqualByComparingTo("0");
            assertThat(repository.calculateTotalBalance(UUID.randomUUID()).block(TIMEOUT)).isNull();
        }
    }

    @Test
    void depositsUseStrictlyPositiveAmountsAndBothInclusiveTimestampBounds() {
        try (var context = context()) {
            var repository = context.getBean(SpaceTransactionRepository.class);
            assertThat(repository.calculateTotalDeposits(SPACE, START, END).block(TIMEOUT))
                    .isEqualByComparingTo("4.2346");
        }
    }

    @Test
    void withdrawalsUseMagnitudeOfStrictlyNegativeAmountsAtBothBounds() {
        try (var context = context()) {
            var repository = context.getBean(SpaceTransactionRepository.class);
            assertThat(repository.calculateTotalWithdrawals(SPACE, START, END).block(TIMEOUT))
                    .isEqualByComparingTo("3.3457");
        }
    }

    @Test
    void derivedTransactionLookupReadsTheActualTimestampColumn() {
        try (var context = context()) {
            var repository = context.getBean(SpaceTransactionRepository.class);
            var transaction =
                    repository
                            .findFirstByAccountSpaceIdAndTransactionDateTimeLessThanEqualOrderByTransactionDateTimeDesc(
                                    SPACE, END)
                            .block(TIMEOUT);
            assertThat(transaction).isNotNull();
            assertThat(transaction.getTransactionDateTime()).isEqualTo(END);
        }
    }

    @Test
    void emptyReversedAndNullAggregateInputsKeepCoalescedZero() {
        try (var context = context()) {
            var repository = context.getBean(SpaceTransactionRepository.class);
            assertThat(
                            repository
                                    .calculateTotalDeposits(UUID.randomUUID(), START, END)
                                    .block(TIMEOUT))
                    .isEqualByComparingTo("0");
            assertThat(repository.calculateTotalWithdrawals(SPACE, END, START).block(TIMEOUT))
                    .isEqualByComparingTo("0");
            assertThat(repository.calculateTotalDeposits(SPACE, null, END).block(TIMEOUT))
                    .isEqualByComparingTo("0");
            assertThat(repository.calculateTotalWithdrawals(null, START, END).block(TIMEOUT))
                    .isEqualByComparingTo("0");
        }
    }

    @Test
    void assetPageAndCountRemainBoundedInTheDatabase() {
        try (var context = context()) {
            var repository = context.getBean(AssetPriceRepository.class);
            var page =
                    repository
                            .findAllAssetPrices(PageRequest.of(1, 2, Sort.by("asOfDatetime")))
                            .collectList()
                            .block(TIMEOUT);
            assertThat(page)
                    .extracting(row -> row.getAssetPriceId().toString())
                    .containsExactly(
                            "00000000-0000-0000-0000-000000000103",
                            "00000000-0000-0000-0000-000000000104");
            assertThat(EXECUTED).anySatisfy(sql -> assertThat(sql).contains("LIMIT 2", "OFFSET 2"));
            assertThat(repository.countAllAssetPrices().block(TIMEOUT)).isEqualTo(5);
        }
    }

    @Test
    void latestPriceUsesBothKeysAndNewestTimestampWithoutBroadeningNullInputs() {
        try (var context = context()) {
            var repository = context.getBean(AssetPriceRepository.class);
            assertThat(repository.findLatestPrice("BTC", "USD").block(TIMEOUT).getPrice())
                    .isEqualByComparingTo("13");
            assertThat(repository.findLatestPrice("missing", "USD").block(TIMEOUT)).isNull();
            assertThat(repository.findLatestPrice(null, "USD").block(TIMEOUT)).isNull();
        }
    }

    @Test
    void pricePurgeKeepsEqualBoundaryAndNullCutoffLeavesRowsIntact() {
        try (var context = context()) {
            var repository = context.getBean(AssetPriceRepository.class);
            repository.deleteByAsOfDatetimeBefore(null).block(TIMEOUT);
            assertThat(repository.countAllAssetPrices().block(TIMEOUT)).isEqualTo(5);
            repository
                    .deleteByAsOfDatetimeBefore(LocalDateTime.of(2026, 1, 3, 0, 0))
                    .block(TIMEOUT);
            assertThat(repository.countAllAssetPrices().block(TIMEOUT)).isEqualTo(3);
            assertThat(repository.findLatestPrice("BTC", "USD").block(TIMEOUT).getPrice())
                    .isEqualByComparingTo("13");
        }
    }
}
