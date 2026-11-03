/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import io.fireflysoftware.core.banking.accounts.models.repositories.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.models.repositories.card.enrollment.impl.AccountCardFenceStoreImpl;
import java.time.Duration;
import java.util.Objects;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransactionImpl;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;

public final class AccountCardFenceSession {
    public enum Role { COMMAND, READER }
    private final Role role;
    private final BoundedR2dbcResources resources;
    private final R2dbcEntityTemplate operations;
    private final AccountCardFenceStoreImpl store;
    private final AccountCardFenceNativeRepository nativeRepository;
    private final BoundedRootTransaction transactions;

    public AccountCardFenceSession(Role role, OwnedLeaseConnectionFactoryImpl factory, DatabaseClient client,
            R2dbcEntityTemplate operations, R2dbcTransactionManager manager) {
        this.role = Objects.requireNonNull(role);
        this.resources = BoundedR2dbcResources.verify(factory, client, operations, manager);
        this.operations = operations;
        this.store = new AccountCardFenceStoreImpl(operations);
        this.nativeRepository = new R2dbcRepositoryFactory(operations).getRepository(AccountCardFenceNativeRepository.class);
        this.transactions = new DefaultBoundedRootTransactionImpl(resources,
                new CleanupPolicy(Duration.ofSeconds(2), Duration.ofSeconds(2)));
    }
    public Role role() { return role; }
    public BoundedR2dbcResources resources() { return resources; }
    public R2dbcEntityTemplate operations() { return operations; }
    public AccountCardFenceStoreImpl store() { return store; }
    public AccountCardFenceNativeRepository nativeRepository() { return nativeRepository; }
    public BoundedRootTransaction transactions() { return transactions; }
}
