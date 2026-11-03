package io.fireflysoftware.core.banking.accounts.models.repositories.crypto;

import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;

import org.springframework.data.domain.Pageable;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface AssetPriceQueries {
    Flux<AssetPrice> findAllAssetPrices(Pageable pageable);

    Mono<Long> countAllAssetPrices();

    Mono<AssetPrice> findLatestPrice(String assetSymbol, String quoteCurrency);

    Mono<Void> deleteByAsOfDatetimeBefore(LocalDateTime date);
}
