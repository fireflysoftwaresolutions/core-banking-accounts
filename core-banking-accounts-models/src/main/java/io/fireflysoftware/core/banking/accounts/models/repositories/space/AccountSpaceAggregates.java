package io.fireflysoftware.core.banking.accounts.models.repositories.space;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountSpaceAggregates {
    Mono<BigDecimal> calculateTotalBalance(UUID accountId);
}
