/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.config.card.enrollment;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceAuthority.Purpose;
import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.impl.*;
import io.r2dbc.postgresql.*;
import io.r2dbc.postgresql.client.SSLMode;
import java.time.Clock;
import java.util.*;
import org.fireflyframework.core.transaction.OwnedLeaseConnectionFactory;
import org.fireflyframework.security.spi.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "firefly.accounts.card-enrollment.enabled", havingValue = "true")
public class AccountCardFenceConfiguration {
    private static final String PREFIX = "firefly.accounts.card-enrollment.";

    @Bean @ConditionalOnMissingBean(AccountCardFenceAuthority.class)
    AccountCardFenceAuthority currentAccountCardFenceAuthority(SecurityContextPort principals, PolicyEvaluationPort policies, Environment environment) {
        if (!environment.getProperty("firefly.security.resource-server.enabled", Boolean.class, false))
            throw new IllegalStateException("Native resource server is required for account card enrollment");
        var clients = new EnumMap<Purpose, Set<String>>(Purpose.class);
        for (Purpose purpose : Purpose.values()) clients.put(purpose, Set.of(required(environment,
                "native." + purpose.name().toLowerCase(Locale.ROOT) + "-client-ids").split(",", -1)));
        var profile = new CurrentAccountCardFenceAuthorityImpl.Profile(required(environment, "native.issuer"),
                required(environment, "native.platform-tenant"), UUID.fromString(required(environment, "native.resource-tenant")),
                required(environment, "native.audience"), clients, required(environment, "native.client-claim"),
                required(environment, "native.actor-claim"), required(environment, "native.party-claim"), required(environment, "native.kind-claim"));
        return new CurrentAccountCardFenceAuthorityImpl(principals, policies, profile, Clock.systemUTC());
    }

    @Bean("accountCardFenceCommandSession")
    AccountCardFenceSession commandSession(Environment environment, ApplicationContext application,
            @Qualifier("r2dbcEntityTemplate") R2dbcEntityTemplate original) {
        var command = database(environment, "command");var reader = database(environment, "reader");
        if (!"firefly_audit_writer".equals(command.username()) || command.username().equals(reader.username())
                || !command.host().equals(reader.host()) || command.port() != reader.port() || !command.database().equals(reader.database()))
            throw new IllegalArgumentException("Exact original audit writer and distinct same-database reader required");
        return session(AccountCardFenceSession.Role.COMMAND, command, original, application);
    }

    @Bean("accountCardFenceReaderSession")
    AccountCardFenceSession readerSession(Environment environment, ApplicationContext application,
            @Qualifier("r2dbcEntityTemplate") R2dbcEntityTemplate original) {
        return session(AccountCardFenceSession.Role.READER, database(environment, "reader"), original, application);
    }

    @Bean
    AccountCardFenceService accountCardFenceService(AccountCardFenceAuthority authority,
            @Qualifier("accountCardFenceCommandSession") AccountCardFenceSession command,
            @Qualifier("accountCardFenceReaderSession") AccountCardFenceSession reader) {
        return new AccountCardFenceServiceImpl(authority, command, reader, Clock.systemUTC(), System::nanoTime, UUID::randomUUID);
    }

    private static AccountCardFenceSession session(AccountCardFenceSession.Role role, Database profile,
            R2dbcEntityTemplate original, ApplicationContext application) {
        var delegate = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(profile.host()).port(profile.port()).database(profile.database()).username(profile.username())
                .password(profile.password()).sslMode(profile.sslMode()).build());
        var factory = new OwnedLeaseConnectionFactory(delegate);var client = DatabaseClient.create(factory);
        var template = new R2dbcEntityTemplate(client, original.getDataAccessStrategy());
        template.setBeanFactory(application);template.setApplicationContext(application);
        return new AccountCardFenceSession(role, factory, client, template, new R2dbcTransactionManager(factory));
    }

    private record Database(String host, int port, String database, String username, String password, SSLMode sslMode) {
        @Override public String toString() { return "AccountCardFenceDatabaseProfile"; }
    }
    private static Database database(Environment environment, String role) {
        String prefix = "database." + role + ".";
        return new Database(required(environment, prefix + "host"), Integer.parseInt(required(environment, prefix + "port")),
                required(environment, prefix + "name"), required(environment, prefix + "username"), required(environment, prefix + "password"),
                SSLMode.valueOf(required(environment, prefix + "ssl-mode").toUpperCase(Locale.ROOT)));
    }
    private static String required(Environment environment, String key) { return environment.getRequiredProperty(PREFIX + key); }
}
