/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface AccountCardFenceService {
    Mono<Observation> acquire(UUID accountId, Acquire request);
    Mono<Observation> claim(UUID accountId, UUID fenceActionId, Claim request);
    Mono<Observation> cancel(UUID accountId, UUID fenceActionId, Cancel request);
    Mono<Observation> recover(UUID accountId, UUID fenceActionId, UUID operationActionId);
}
