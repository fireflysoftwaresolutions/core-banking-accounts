/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.AccountCardFenceAuthority.Permit;
import io.fireflysoftware.core.banking.accounts.models.repositories.card.enrollment.AccountCardFenceNativeRepository.Stamp;
import java.time.*;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.*;

public final class AccountCardFenceWindow {
    private static final Duration MAXIMUM = Duration.ofSeconds(5);
    private final Clock clock;
    private final LongSupplier nanos;
    private final long beforeAuthorization;
    private final Instant started;
    private R2dbcOperationBudget budget;

    public AccountCardFenceWindow(Clock clock, LongSupplier nanos) {
        this.clock = clock;this.nanos = nanos;this.beforeAuthorization = nanos.getAsLong();this.started = clock.instant();
        budget = R2dbcOperationBudget.observed(started, started.plus(MAXIMUM), beforeAuthorization, MAXIMUM, clock, nanos);
    }
    public void constrain(Permit permit) {
        budget = budget.intersect(R2dbcOperationBudget.observed(permit.observedAt(), permit.effectiveUntil(), beforeAuthorization, MAXIMUM, clock, nanos));
        budget.remaining();
    }
    public Instant commandUntil(Permit permit) { return permit.effectiveUntil().isBefore(started.plus(MAXIMUM)) ? permit.effectiveUntil() : started.plus(MAXIMUM); }
    public Duration remaining() { return budget.remaining(); }
    public R2dbcOperationBudget budget() { budget.remaining();return budget; }
    public long beforeNative() { remaining();return nanos.getAsLong(); }
    public void installed(UUID nonce, Instant requestedUntil, Stamp actual, long beforeNative, RootTransactionScope scope) {
        if (actual == null || !nonce.equals(actual.contextNonce()) || actual.databaseTime() == null
                || actual.commandUntil() == null || actual.commandUntil().isAfter(requestedUntil))
            throw new IllegalStateException("ACTUAL_ACCOUNT_FENCE_DATABASE_STAMP_REQUIRED");
        var nativeBudget = R2dbcOperationBudget.observed(actual.databaseTime(), actual.commandUntil().minusMillis(1), beforeNative, MAXIMUM, clock, nanos);
        budget = budget.intersect(nativeBudget);scope.narrow(nativeBudget);budget.remaining();
    }
}
