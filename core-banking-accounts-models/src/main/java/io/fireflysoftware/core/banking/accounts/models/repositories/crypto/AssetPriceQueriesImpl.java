package io.fireflysoftware.core.banking.accounts.models.repositories.crypto;

import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.Table;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public class AssetPriceQueriesImpl implements AssetPriceQueries {
    private final R2dbcEntityOperations operations;

    public AssetPriceQueriesImpl(R2dbcEntityOperations operations) {
        this.operations = operations;
    }

    @Override
    public Flux<AssetPrice> findAllAssetPrices(Pageable pageable) {
        var mapper =
                operations.getDataAccessStrategy().getStatementMapper().forType(AssetPrice.class);
        var select =
                mapper.createSelect("asset_price")
                        .withProjection(Table.create("asset_price").asterisk());
        if (pageable != null) select = select.withPage(pageable);
        return operations.query(mapper.getMappedObject(select), AssetPrice.class).all();
    }

    @Override
    public Mono<Long> countAllAssetPrices() {
        return operations.count(Query.empty(), AssetPrice.class);
    }

    @Override
    public Mono<AssetPrice> findLatestPrice(String assetSymbol, String quoteCurrency) {
        if (assetSymbol == null || quoteCurrency == null) return Mono.empty();
        var mapper =
                operations.getDataAccessStrategy().getStatementMapper().forType(AssetPrice.class);
        var select =
                mapper.createSelect("asset_price")
                        .withProjection(Table.create("asset_price").asterisk())
                        .withCriteria(
                                Criteria.where("assetSymbol")
                                        .is(assetSymbol)
                                        .and("quoteCurrency")
                                        .is(quoteCurrency))
                        .withSort(Sort.by(Sort.Direction.DESC, "asOfDatetime"))
                        .limit(1);
        return operations.query(mapper.getMappedObject(select), AssetPrice.class).one();
    }

    @Override
    public Mono<Void> deleteByAsOfDatetimeBefore(LocalDateTime date) {
        if (date == null) return Mono.empty();
        return operations
                .delete(
                        Query.query(Criteria.where("asOfDatetime").lessThan(date)),
                        AssetPrice.class)
                .then();
    }
}
