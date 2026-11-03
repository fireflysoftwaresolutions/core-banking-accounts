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

package io.fireflysoftware.core.banking.accounts.models.entities.crypto;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents price information for crypto assets and tokenized assets. Tracks historical price data
 * for various assets against different quote currencies.
 *
 * <p>Business Rules: - Each asset can have multiple price records over time - Each asset can be
 * priced in multiple quote currencies - Price data should include source information for audit
 * purposes - Historical price records should be maintained - Price updates must be timestamped
 */
@Table("asset_price")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class AssetPrice {

    /** Unique identifier for the asset price record Example: 1000001 */
    @Id private UUID assetPriceId;

    /**
     * Symbol or ticker of the crypto asset Examples: - "BTC" - Bitcoin - "ETH" - Ethereum - "USDC"
     * - USD Coin - "SOL" - Solana
     */
    private String assetSymbol;

    /**
     * Currency in which the asset is priced ISO 4217 currency code for fiat currencies Examples: -
     * "USD" - US Dollar - "EUR" - Euro - "BTC" - Bitcoin (when pricing altcoins in BTC)
     */
    private String quoteCurrency;

    /**
     * The price of the asset in the quote currency Stored with high precision (up to 18 decimal
     * places for crypto) Examples: - 45000.00 (BTC/USD) - 0.06 (ETH/BTC) - 1.00 (USDC/USD)
     */
    private BigDecimal price;

    /**
     * Timestamp when this price was recorded Used for: - Historical tracking - Time-series analysis
     * - Point-in-time valuation Example: 2024-01-15T14:30:00.000Z
     */
    private LocalDateTime asOfDatetime;

    /**
     * Source of the price data Examples: - "Coinbase" - Coinbase exchange - "Binance" - Binance
     * exchange - "CoinMarketCap" - CoinMarketCap API - "ChainLink" - ChainLink oracle - "Internal"
     * - Internal pricing model
     */
    private String priceSource;

    @CreatedDate
    @Column("created_date")
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "dd/MM/yyyy'T'HH:mm:ss.SSSSSS",
            timezone = "Europe/Madrid")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private LocalDateTime dateCreated;

    @LastModifiedDate
    @Column("last_modified_date")
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "dd/MM/yyyy'T'HH:mm:ss.SSSSSS",
            timezone = "Europe/Madrid")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private LocalDateTime dateUpdated;

    @Column("created_by")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private String createdBy;

    @Column("last_modified_by")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private String lastModifiedBy;

    public AssetPrice(
            UUID assetPriceId,
            String assetSymbol,
            String quoteCurrency,
            BigDecimal price,
            LocalDateTime asOfDatetime,
            String priceSource) {
        this.assetPriceId = assetPriceId;
        this.assetSymbol = assetSymbol;
        this.quoteCurrency = quoteCurrency;
        this.price = price;
        this.asOfDatetime = asOfDatetime;
        this.priceSource = priceSource;
    }
}
