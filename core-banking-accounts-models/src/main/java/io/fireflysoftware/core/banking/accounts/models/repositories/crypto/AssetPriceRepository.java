/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.banking.accounts.models.repositories.crypto;

import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for managing AssetPrice entities.
 * Provides methods for CRUD operations and custom queries for asset prices.
 */
@Repository
public interface AssetPriceRepository extends ReactiveCrudRepository<AssetPrice, UUID>, AssetPriceQueries {

    /**
     * Find all asset prices with pagination.
     *
     * @param pageable pagination information
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findAllAssetPrices(Pageable pageable);
    
    /**
     * Count all asset prices.
     *
     * @return a Mono of Long representing the count
     */
    Mono<Long> countAllAssetPrices();

    /**
     * Find all asset prices for a specific asset symbol.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbol(String assetSymbol);
    
    /**
     * Find all asset prices for a specific asset symbol with pagination.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param pageable pagination information
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbol(String assetSymbol, Pageable pageable);
    
    /**
     * Count all asset prices for a specific asset symbol.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @return a Mono of Long representing the count
     */
    Mono<Long> countByAssetSymbol(String assetSymbol);

    /**
     * Find all asset prices for a specific asset symbol and quote currency.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndQuoteCurrency(String assetSymbol, String quoteCurrency);
    
    /**
     * Find all asset prices for a specific asset symbol and quote currency with pagination.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @param pageable pagination information
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndQuoteCurrency(String assetSymbol, String quoteCurrency, Pageable pageable);
    
    /**
     * Count all asset prices for a specific asset symbol and quote currency.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @return a Mono of Long representing the count
     */
    Mono<Long> countByAssetSymbolAndQuoteCurrency(String assetSymbol, String quoteCurrency);

    /**
     * Find all asset prices for a specific asset symbol and quote currency, ordered by asOfDatetime descending.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @param pageable pagination information
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndQuoteCurrencyOrderByAsOfDatetimeDesc(String assetSymbol, String quoteCurrency, Pageable pageable);

    /**
     * Find the latest asset price for a specific asset symbol and quote currency.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @return a Mono of AssetPrice entity
     */
    Mono<AssetPrice> findLatestPrice(String assetSymbol, String quoteCurrency);

    /**
     * Find all asset prices for a specific asset symbol within a date range.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndAsOfDatetimeBetween(String assetSymbol, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find all asset prices for a specific asset symbol within a date range with pagination.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @param pageable pagination information
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndAsOfDatetimeBetween(String assetSymbol, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Count all asset prices for a specific asset symbol within a date range.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @return a Mono of Long representing the count
     */
    Mono<Long> countByAssetSymbolAndAsOfDatetimeBetween(String assetSymbol, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find all asset prices for a specific asset symbol and quote currency within a date range.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndQuoteCurrencyAndAsOfDatetimeBetween(
            String assetSymbol, String quoteCurrency, LocalDateTime startDate, LocalDateTime endDate);
            
    /**
     * Find all asset prices for a specific asset symbol and quote currency within a date range with pagination.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @param pageable pagination information
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByAssetSymbolAndQuoteCurrencyAndAsOfDatetimeBetween(
            String assetSymbol, String quoteCurrency, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
            
    /**
     * Count all asset prices for a specific asset symbol and quote currency within a date range.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @param startDate the start date of the range
     * @param endDate the end date of the range
     * @return a Mono of Long representing the count
     */
    Mono<Long> countByAssetSymbolAndQuoteCurrencyAndAsOfDatetimeBetween(
            String assetSymbol, String quoteCurrency, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find all asset prices from a specific price source.
     *
     * @param priceSource the source of the price data (e.g., "Coinbase", "Binance")
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByPriceSource(String priceSource);

    /**
     * Find all asset prices for a specific quote currency.
     *
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @return a Flux of AssetPrice entities
     */
    Flux<AssetPrice> findByQuoteCurrency(String quoteCurrency);

    /**
     * Delete all asset prices for a specific asset symbol.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @return a Mono of Void
     */
    Mono<Void> deleteByAssetSymbol(String assetSymbol);

    /**
     * Delete all asset prices for a specific asset symbol and quote currency.
     *
     * @param assetSymbol the symbol of the asset (e.g., "BTC", "ETH")
     * @param quoteCurrency the currency in which the asset is priced (e.g., "USD", "EUR")
     * @return a Mono of Void
     */
    Mono<Void> deleteByAssetSymbolAndQuoteCurrency(String assetSymbol, String quoteCurrency);

    /**
     * Delete all asset prices older than a specific date.
     *
     * @param date the cutoff date
     * @return a Mono of Void
     */
    Mono<Void> deleteByAsOfDatetimeBefore(LocalDateTime date);
}