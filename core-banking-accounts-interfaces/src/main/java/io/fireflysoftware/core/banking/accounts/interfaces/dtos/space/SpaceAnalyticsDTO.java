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


package io.fireflysoftware.core.banking.accounts.interfaces.dtos.space;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for account space analytics.
 * Contains detailed analytics information for a specific account space over a period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpaceAnalyticsDTO {
    // Space identification
    @NotNull(message = "Account space ID is required")
    private UUID accountSpaceId;

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotBlank(message = "Space name is required")
    @Size(max = 100, message = "Space name must not exceed 100 characters")
    private String spaceName;

    @NotNull(message = "Space type is required")
    private AccountSpaceTypeEnum spaceType;

    // Analysis period
    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    @NotNull(message = "End date is required")
    private LocalDateTime endDate;
    
    // Balance metrics
    @DecimalMin(value = "0.0", inclusive = true, message = "Opening balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Opening balance must have at most 15 integer digits and 2 decimal places")
    private BigDecimal openingBalance;

    @DecimalMin(value = "0.0", inclusive = true, message = "Closing balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Closing balance must have at most 15 integer digits and 2 decimal places")
    private BigDecimal closingBalance;

    @DecimalMin(value = "0.0", inclusive = true, message = "Lowest balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Lowest balance must have at most 15 integer digits and 2 decimal places")
    private BigDecimal lowestBalance;

    @DecimalMin(value = "0.0", inclusive = true, message = "Highest balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Highest balance must have at most 15 integer digits and 2 decimal places")
    private BigDecimal highestBalance;

    @DecimalMin(value = "0.0", inclusive = true, message = "Average balance cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Average balance must have at most 15 integer digits and 2 decimal places")
    private BigDecimal averageBalance;

    // Growth metrics
    @Digits(integer = 15, fraction = 2, message = "Net change must have at most 15 integer digits and 2 decimal places")
    private BigDecimal netChange;

    @DecimalMin(value = "-100.0", message = "Net change percentage cannot be less than -100%")
    @Digits(integer = 5, fraction = 2, message = "Net change percentage must have at most 5 integer digits and 2 decimal places")
    private BigDecimal netChangePercentage;

    @Digits(integer = 5, fraction = 2, message = "Annualized growth rate must have at most 5 integer digits and 2 decimal places")
    private BigDecimal annualizedGrowthRate;

    // Transaction metrics
    @Min(value = 0, message = "Total transactions cannot be negative")
    private Integer totalTransactions;

    @Min(value = 0, message = "Incoming transactions cannot be negative")
    private Integer incomingTransactions;

    @Min(value = 0, message = "Outgoing transactions cannot be negative")
    private Integer outgoingTransactions;

    @DecimalMin(value = "0.0", inclusive = true, message = "Total inflow cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Total inflow must have at most 15 integer digits and 2 decimal places")
    private BigDecimal totalInflow;

    @DecimalMin(value = "0.0", inclusive = true, message = "Total outflow cannot be negative")
    @Digits(integer = 15, fraction = 2, message = "Total outflow must have at most 15 integer digits and 2 decimal places")
    private BigDecimal totalOutflow;

    @Digits(integer = 15, fraction = 2, message = "Average transaction amount must have at most 15 integer digits and 2 decimal places")
    private BigDecimal averageTransactionAmount;
    
    // Time-series data
    private List<TimeSeriesDataPoint> balanceHistory;
    private Map<String, BigDecimal> categoryDistribution;
    
    // Goal tracking (if applicable)
    private BigDecimal goalProgress;
    private BigDecimal projectedCompletionPercentage;
    private LocalDateTime projectedCompletionDate;
    
    // Comparison to other spaces
    private BigDecimal percentageOfTotalAccountBalance;
    private Integer rankByBalance;
    private Integer rankByGrowth;
    
    /**
     * Represents a single data point in a time series.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesDataPoint {
        private LocalDateTime timestamp;
        private BigDecimal value;
    }
}
