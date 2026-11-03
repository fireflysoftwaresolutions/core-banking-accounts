package io.fireflysoftware.core.banking.accounts.models.repositories.space;

import io.fireflysoftware.core.banking.accounts.models.entities.space.AccountSpace;

import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.Table;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountSpaceAggregatesImpl implements AccountSpaceAggregates {
    private final R2dbcEntityOperations operations;

    public AccountSpaceAggregatesImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Mono<BigDecimal> calculateTotalBalance(UUID accountId) {
        if (accountId == null) return Mono.empty();
        var mapper =
                operations.getDataAccessStrategy().getStatementMapper().forType(AccountSpace.class);
        var total =
                SimpleFunction.create(
                                "SUM", List.of(Table.create("account_space").column("balance")))
                        .as("total");
        var select =
                mapper.createSelect("account_space")
                        .withProjection(total)
                        .withCriteria(Criteria.where("accountId").is(accountId));
        return operations
                .query(
                        mapper.getMappedObject(select),
                        (row, metadata) -> Optional.ofNullable(row.get("total", BigDecimal.class)))
                .one()
                .flatMap(Mono::justOrEmpty);
    }
}
