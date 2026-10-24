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


package io.fireflysoftware.core.banking.accounts.models.entities.parameter;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.parameter.ParamTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores configurable parameters for accounts.
 * Allows for time-based parameter values with effective dates.
 *
 * Business Rules:
 * - Parameters can have different values for different time periods
 * - Parameters must have an effective date
 * - Parameter values must be stored with 4 decimal places
 * - Overlapping effective periods are not allowed for the same parameter type
 */
@Table("account_parameter")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountParameter extends BaseEntity {

    /**
     * Unique identifier for the parameter record
     * Example: 1000001
     */
    @Id
    private UUID accountParameterId;

    /**
     * Reference to the associated account
     * Example: 100001 (links to Account.accountId)
     */
    private UUID accountId;

    /**
     * Type of parameter being configured
     * Examples with typical values:
     * - MONTHLY_FEE: 5.00 EUR
     * - OVERDRAFT_LIMIT: 1000.00 EUR
     * - INTEREST_RATE: 2.5000 PERCENT
     */
    private ParamTypeEnum paramType;

    /**
     * The parameter value
     * Stored with 4 decimal places for high precision
     * Examples:
     * - 5.0000 (Monthly fee of 5 EUR)
     * - 1000.0000 (Overdraft limit of 1000 EUR)
     * - 2.5000 (Interest rate of 2.5%)
     */
    private BigDecimal paramValue;

    /**
     * Unit of measurement for the parameter
     * Examples:
     * - "EUR" for monetary values
     * - "PERCENT" for rates
     * - "USD" for dollar amounts
     */
    private String paramUnit;

    /**
     * When this parameter value becomes effective
     * Example: 2024-01-15T00:00:00.000Z
     */
    private LocalDateTime effectiveDate;

    /**
     * When this parameter value expires
     * Null if no expiry is planned
     * Example: 2024-12-31T23:59:59.999Z
     */
    private LocalDateTime expiryDate;

    /**
     * Optional description of the parameter setting
     * Examples:
     * - "Promotional interest rate for Q1 2024"
     * - "Increased overdraft limit - good standing"
     * - "Reduced monthly fee - senior account"
     */
    private String description;
}

