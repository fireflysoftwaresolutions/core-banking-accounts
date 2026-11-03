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

package io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.BaseDTO;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for asset price information. Used for transferring crypto asset price data
 * between layers.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AssetPriceDTO extends BaseDTO {

    /** Unique identifier for the asset price record */
    @JsonProperty(access = JsonProperty.Access.READ_WRITE)
    @io.swagger.v3.oas.annotations.media.Schema(
            accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private UUID assetPriceId;

    /** Symbol or ticker of the crypto asset Examples: "BTC", "ETH", "USDC", "SOL" */
    @NotBlank(message = "Asset symbol is required")
    @Size(max = 20, message = "Asset symbol must not exceed 20 characters")
    @Pattern(
            regexp = "^[A-Z0-9]+$",
            message = "Asset symbol must contain only uppercase letters and numbers")
    private String assetSymbol;

    /** Currency in which the asset is priced Examples: "USD", "EUR", "BTC" */
    @NotBlank(message = "Quote currency is required")
    @Size(min = 1, max = 10, message = "Quote instrument must contain 1 to 10 characters")
    @Pattern(
            regexp = "^[A-Z0-9]+$",
            message = "Quote instrument must contain only uppercase letters and numbers")
    private String quoteCurrency;

    /** The price of the asset in the quote currency */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    @io.swagger.v3.oas.annotations.media.Schema(
            minimum = "0",
            exclusiveMinimum = true,
            maximum = "999999999999999999.999999999999999999",
            multipleOf = 0.000000000000000001)
    @Digits(
            integer = 18,
            fraction = 18,
            message = "Price must have at most 18 integer digits and 18 decimal places")
    private BigDecimal price;

    /** Timestamp when this price was recorded */
    @NotNull(message = "As of datetime is required")
    @PastOrPresent(message = "As of datetime cannot be in the future")
    private LocalDateTime asOfDatetime;

    /** Source of the price data Examples: "Coinbase", "Binance", "CoinMarketCap" */
    @NotBlank(message = "Price source is required")
    @Size(max = 50, message = "Price source must not exceed 50 characters")
    private String priceSource;
}
