package io.fireflysoftware.core.banking.accounts.models.repositories.space;

import io.fireflysoftware.core.banking.accounts.models.entities.space.SpaceTransaction;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Table;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SpaceTransactionAggregatesImpl implements SpaceTransactionAggregates {
    private final R2dbcEntityOperations operations;

    public SpaceTransactionAggregatesImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Mono<BigDecimal> calculateTotalDeposits(
            UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate) {
        return total(accountSpaceId, startDate, endDate, false);
    }

    @Override
    public Mono<BigDecimal> calculateTotalWithdrawals(
            UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate) {
        return total(accountSpaceId, startDate, endDate, true);
    }

    private Mono<BigDecimal> total(
            UUID accountSpaceId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            boolean withdrawals) {
        if (accountSpaceId == null || startDate == null || endDate == null)
            return Mono.just(BigDecimal.ZERO);
        var mapper =
                operations
                        .getDataAccessStrategy()
                        .getStatementMapper()
                        .forType(SpaceTransaction.class);
        Expression amount = Table.create("space_transaction").column("amount");
        if (withdrawals) amount = SimpleFunction.create("ABS", List.of(amount));
        var projection = SimpleFunction.create("SUM", List.of(amount)).as("total");
        var sign =
                withdrawals
                        ? Criteria.where("amount").lessThan(BigDecimal.ZERO)
                        : Criteria.where("amount").greaterThan(BigDecimal.ZERO);
        var criteria =
                Criteria.where("accountSpaceId")
                        .is(accountSpaceId)
                        .and("transactionDateTime")
                        .greaterThanOrEquals(startDate)
                        .and("transactionDateTime")
                        .lessThanOrEquals(endDate)
                        .and(sign);
        var select =
                mapper.createSelect("space_transaction")
                        .withProjection(projection)
                        .withCriteria(criteria);
        return operations
                .query(
                        mapper.getMappedObject(select),
                        (row, metadata) -> Optional.ofNullable(row.get("total", BigDecimal.class)))
                .one()
                .map(total -> total.orElse(BigDecimal.ZERO));
    }
}
