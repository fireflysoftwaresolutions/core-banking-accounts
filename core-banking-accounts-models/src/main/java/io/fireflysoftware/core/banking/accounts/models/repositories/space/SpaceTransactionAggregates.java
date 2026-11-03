package io.fireflysoftware.core.banking.accounts.models.repositories.space;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface SpaceTransactionAggregates {
    Mono<BigDecimal> calculateTotalDeposits(
            UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);

    Mono<BigDecimal> calculateTotalWithdrawals(
            UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate);
}
