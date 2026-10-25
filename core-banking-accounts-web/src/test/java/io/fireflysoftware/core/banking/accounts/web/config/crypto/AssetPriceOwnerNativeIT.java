package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.core.banking.accounts.core.mappers.crypto.AssetPriceMapper;
import io.fireflysoftware.core.banking.accounts.core.services.crypto.AssetPriceService;
import io.fireflysoftware.core.banking.accounts.core.services.crypto.impl.AssetPriceServiceImpl;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;
import io.r2dbc.postgresql.*;
import io.r2dbc.spi.ConnectionFactory;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.domain.*;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;

class AssetPriceOwnerNativeIT {
    static final String ISSUER = "https://owned-asset-native.example.test";
    static final Duration TIMEOUT = Duration.ofSeconds(8);
    static AnnotationConfigApplicationContext application;
    AssetPriceService service;
    AssetPriceRepository repository;

    @Configuration(proxyBeanMethods = false)
    @Profile("asset-price-native-fixture")
    @EnableR2dbcAuditing
    @EnableTransactionManagement
    @EnableR2dbcRepositories(basePackageClasses = AssetPriceRepository.class)
    @Import({
        AssetPriceServiceImpl.class,
        AssetPriceMapper.class,
        AssetPriceAuditConfiguration.class
    })
    static class Database
            extends io.fireflysoftware.core.banking.accounts.models.config.R2dbcConfiguration {
        @Bean
        @Override
        public ConnectionFactory connectionFactory() {
            return new PostgresqlConnectionFactory(
                    PostgresqlConnectionConfiguration.builder()
                            .host("127.0.0.1")
                            .port(Integer.parseInt(System.getProperty("accounts.native.port")))
                            .database("postgres")
                            .username("accounts_asset_runtime")
                            .password(System.getenv("ACCOUNTS_NATIVE_PASSWORD"))
                            .build());
        }

        @Bean
        ReactiveTransactionManager transactionManager(ConnectionFactory factory) {
            return new R2dbcTransactionManager(factory);
        }

        @Bean
        SecurityContextPort assetNativePrincipalContext() {
            return new ReactorSecurityContextAdapter();
        }
    }

    @BeforeAll
    static void spring() {
        application = new AnnotationConfigApplicationContext();
        application.getEnvironment().setActiveProfiles("asset-price-native-fixture");
        application
                .getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "owned-asset-native", Map.of("assets.security.issuer", ISSUER)));
        application.register(Database.class);
        application.refresh();
    }

    @AfterAll
    static void stop() {
        if (application != null) application.close();
    }

    @BeforeEach
    void repositories() {
        service = application.getBean(AssetPriceService.class);
        repository = application.getBean(AssetPriceRepository.class);
    }

    SecurityPrincipal actor(String subject, Instant expiry) {
        return SecurityPrincipal.builder()
                .subject(subject)
                .issuer(ISSUER)
                .scopes(Set.of(AssetPriceActorRules.WRITE_SCOPE))
                .claims(Map.of("exp", expiry))
                .build();
    }

    <T> T as(Mono<T> work, String actor) {
        return as(work, actor(actor, Instant.now().plusSeconds(60)));
    }

    <T> T as(Mono<T> work, SecurityPrincipal principal) {
        return work.contextWrite(
                        ReactiveSecurityContextHolder.withAuthentication(
                                new FireflyAuthenticationToken(principal)))
                .block(TIMEOUT);
    }

    AssetPriceDTO dto(String symbol, int day) {
        var d = new AssetPriceDTO();
        d.setAssetSymbol(symbol);
        d.setQuoteCurrency("USD");
        d.setPrice(new BigDecimal("123456789012345678.123456789012345678"));
        d.setAsOfDatetime(LocalDateTime.of(2026, 1, day, 0, 0));
        d.setPriceSource("owned-native-price");
        return d;
    }

    String symbol() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }

    Map<String, Object> audit(UUID id) throws Exception {
        try (var c =
                        DriverManager.getConnection(
                                "jdbc:postgresql://127.0.0.1:"
                                        + System.getProperty("accounts.native.port")
                                        + "/postgres",
                                "accounts_asset_runtime",
                                System.getenv("ACCOUNTS_NATIVE_PASSWORD"));
                var q =
                        c.prepareStatement(
                                "select"
                                    + " created_by,last_modified_by,created_date,last_modified_date,char_length(created_by)"
                                    + " as actor_length,price from asset_price where"
                                    + " asset_price_id=?")) {
            q.setObject(1, id);
            try (var rows = q.executeQuery()) {
                assertThat(rows.next()).isTrue();
                Map<String, Object> out = new HashMap<>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++)
                    out.put(rows.getMetaData().getColumnLabel(i), rows.getObject(i));
                return out;
            }
        }
    }

    @Test
    void actualInsertHasServerIdentityCreatorAndStandardDateAuditing() throws Exception {
        var input = dto(symbol(), 1);
        input.setDateCreated(LocalDateTime.of(2000, 1, 1, 0, 0));
        input.setDateUpdated(input.getDateCreated());
        var created = as(service.createAssetPrice(input), "first-verified");
        assertThat(created.getAssetPriceId()).isNotNull();
        assertThat(created.getDateCreated()).isNotNull().isNotEqualTo(input.getDateCreated());
        assertThat(created.getDateUpdated()).isNotNull();
        var row = audit(created.getAssetPriceId());
        assertThat(row)
                .containsEntry("created_by", "first-verified")
                .containsEntry("last_modified_by", "first-verified");
        assertThat((BigDecimal) row.get("price")).isEqualByComparingTo(input.getPrice());
    }

    @Test
    void exactDatabaseRepresentationPersistsAUsdcInstrumentPair() throws Exception {
        var input = dto("A".repeat(20), 1);
        input.setQuoteCurrency("USDC");
        var created = as(service.createAssetPrice(input), "verified-usdc-writer");
        assertThat(created.getAssetSymbol()).isEqualTo(input.getAssetSymbol());
        assertThat(created.getQuoteCurrency()).isEqualTo("USDC");
        assertThat((BigDecimal) audit(created.getAssetPriceId()).get("price"))
                .isEqualByComparingTo(input.getPrice());
    }

    @Test
    void actualUpdateRetainsCreationIdentityAndTimeAndStampsNextModifier() throws Exception {
        var input = dto(symbol(), 1);
        var original = as(service.createAssetPrice(input), "original-verified");
        var old = audit(original.getAssetPriceId());
        var update = dto(input.getAssetSymbol(), 2);
        update.setAssetPriceId(UUID.randomUUID());
        update.setDateCreated(LocalDateTime.of(2000, 1, 1, 0, 0));
        var result =
                as(service.updateAssetPrice(original.getAssetPriceId(), update), "next-verified");
        assertThat(result.getAssetPriceId()).isEqualTo(original.getAssetPriceId());
        assertThat(result.getDateCreated()).isEqualTo(original.getDateCreated());
        var current = audit(result.getAssetPriceId());
        assertThat(current)
                .containsEntry("created_by", "original-verified")
                .containsEntry("last_modified_by", "next-verified");
        assertThat(current.get("created_date")).isEqualTo(old.get("created_date"));
    }

    @Test
    void createAssignedIdentityIsRejectedBeforePersistence() {
        var input = dto(symbol(), 1);
        input.setAssetPriceId(UUID.randomUUID());
        long count = repository.count().block(TIMEOUT);
        assertThatThrownBy(() -> as(service.createAssetPrice(input), "verified"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(repository.count().block(TIMEOUT)).isEqualTo(count);
    }

    @Test
    void missingExpiredAndOverlongIdentityCannotWrite() {
        long count = repository.count().block(TIMEOUT);
        assertThatThrownBy(() -> service.createAssetPrice(dto(symbol(), 1)).block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                as(
                                        service.createAssetPrice(dto(symbol(), 1)),
                                        actor("verified", Instant.now().minusSeconds(1))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> as(service.createAssetPrice(dto(symbol(), 1)), "😀".repeat(51)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(repository.count().block(TIMEOUT)).isEqualTo(count);
    }

    @Test
    void unicodeIdentityUsesFiftyPostgresCharactersWithoutTruncation() throws Exception {
        var subject = "😀".repeat(50);
        var created = as(service.createAssetPrice(dto(symbol(), 1)), subject);
        assertThat(audit(created.getAssetPriceId()))
                .containsEntry("created_by", subject)
                .containsEntry("actor_length", 50);
    }

    @Test
    void derivedAndPreparedQueriesMapAuditColumnsAndKeepInclusivePaging() {
        var symbol = symbol();
        var first = as(service.createAssetPrice(dto(symbol, 1)), "verified");
        as(service.createAssetPrice(dto(symbol, 2)), "verified");
        assertThat(
                        repository
                                .findByAssetSymbolAndQuoteCurrencyAndAsOfDatetimeBetween(
                                        symbol,
                                        "USD",
                                        LocalDateTime.of(2026, 1, 1, 0, 0),
                                        LocalDateTime.of(2026, 1, 2, 0, 0),
                                        PageRequest.of(0, 10, Sort.by("asOfDatetime")))
                                .collectList()
                                .block(TIMEOUT))
                .hasSize(2)
                .allSatisfy(
                        p -> {
                            assertThat(p.getDateCreated()).isNotNull();
                            assertThat(p.getCreatedBy()).isEqualTo("verified");
                        });
        assertThat(repository.findLatestPrice(symbol, "USD").block(TIMEOUT).getAsOfDatetime())
                .isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
        assertThat(
                        repository
                                .findAllAssetPrices(PageRequest.of(0, 1, Sort.by("dateCreated")))
                                .collectList()
                                .block(TIMEOUT))
                .hasSize(1);
        assertThat(service.getAssetPrice(first.getAssetPriceId()).block(TIMEOUT).getDateCreated())
                .isEqualTo(first.getDateCreated());
    }

    @Test
    void allExistingDeletePrimitivesRemainBounded() {
        var symbol = symbol();
        var row = as(service.createAssetPrice(dto(symbol, 1)), "verified");
        as(service.deleteAssetPrice(row.getAssetPriceId()), "verified");
        assertThat(repository.findById(row.getAssetPriceId()).block(TIMEOUT)).isNull();
        as(service.createAssetPrice(dto(symbol, 1)), "verified");
        as(repository.deleteByAssetSymbolAndQuoteCurrency(symbol, "USD"), "verified");
        assertThat(repository.countByAssetSymbol(symbol).block(TIMEOUT)).isZero();
        as(service.createAssetPrice(dto(symbol, 1)), "verified");
        as(repository.deleteByAsOfDatetimeBefore(LocalDateTime.of(2025, 1, 1, 0, 0)), "verified");
        assertThat(repository.countByAssetSymbol(symbol).block(TIMEOUT)).isEqualTo(1);
        as(repository.deleteByAssetSymbol(symbol), "verified");
        assertThat(repository.countByAssetSymbol(symbol).block(TIMEOUT)).isZero();
    }

    @Test
    void sameFactoryOuterRollbackRemovesAuditedInsert() {
        long count = repository.count().block(TIMEOUT);
        var tx =
                TransactionalOperator.create(application.getBean(ReactiveTransactionManager.class));
        var command =
                service.createAssetPrice(dto(symbol(), 1))
                        .then(Mono.error(new IllegalStateException("owned asset rollback")))
                        .as(tx::transactional);
        assertThatThrownBy(() -> as(command, "verified"))
                .hasMessageContaining("owned asset rollback");
        assertThat(repository.count().block(TIMEOUT)).isEqualTo(count);
    }
}
