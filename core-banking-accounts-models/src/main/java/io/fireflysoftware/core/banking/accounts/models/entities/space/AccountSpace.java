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


package io.fireflysoftware.core.banking.accounts.models.entities.space;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
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
 * Represents a space or bucket within a banking account.
 * Spaces allow customers to organize their money for different purposes within a single account.
 *
 * Business Rules:
 * - Each account must have at least one MAIN space
 * - Spaces can have their own balance, but the sum of all space balances must equal the account's total balance
 * - Spaces can have goals and target amounts
 * - Custom spaces can be created with user-defined names
 * - Spaces can have automatic transfers configured
 */
@Table("account_space")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountSpace extends BaseEntity {

    /**
     * Unique identifier for the space
     * Example: 1000001
     */
    @Id
    private UUID accountSpaceId;

    /**
     * Reference to the associated account
     * Example: 100001 (links to Account.accountId)
     */
    private UUID accountId;

    /**
     * Name of the space
     * Examples:
     * - "Main Account" (for MAIN type)
     * - "Vacation Fund" (for VACATION type)
     * - "Emergency Fund" (for EMERGENCY type)
     * - "New Car" (for CUSTOM type)
     */
    private String spaceName;

    /**
     * Type of space
     * MAIN: Primary account space (required)
     * SAVINGS: For saving money
     * VACATION: For vacation planning
     * EMERGENCY: For emergency funds
     * GOALS: For specific financial goals
     * CUSTOM: User-defined purpose
     */
    private AccountSpaceTypeEnum spaceType;

    /**
     * Current balance in this space
     * Stored with 4 decimal places for high precision
     * Examples:
     * - 1000.0000 (One thousand)
     * - 50.5000 (Fifty and fifty cents)
     */
    private BigDecimal balance;

    /**
     * Optional target amount for goal-based spaces
     * Null if no specific goal is set
     * Example: 5000.0000 (Five thousand target)
     */
    private BigDecimal targetAmount;

    /**
     * Optional target date for goal-based spaces
     * Null if no specific deadline is set
     * Example: 2024-12-31T00:00:00.000Z (End of year target)
     */
    private LocalDateTime targetDate;

    /**
     * Optional icon or image identifier for UI display
     * Example: "vacation_icon", "savings_icon", "custom_icon_1"
     */
    private String iconId;

    /**
     * Optional color code for UI display
     * Example: "#FF5733", "blue", "green"
     */
    private String colorCode;

    /**
     * Optional description or notes about the space
     * Examples:
     * - "Saving for summer vacation in Italy"
     * - "Emergency fund - goal is 3 months of expenses"
     * - "New car down payment"
     */
    private String description;

    /**
     * Whether this space is visible in the main UI
     * Default is true
     * Can be used to hide spaces without deleting them
     */
    private Boolean isVisible;

    /**
     * Whether automatic transfers are enabled for this space
     * Default is false
     */
    private Boolean enableAutomaticTransfers;

    /**
     * Frequency of automatic transfers
     * Examples: DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUALLY
     */
    private io.fireflysoftware.core.banking.accounts.interfaces.enums.space.TransferFrequencyEnum transferFrequency;

    /**
     * Amount to transfer automatically
     * Example: 50.0000 (Fifty per period)
     */
    private BigDecimal transferAmount;

    /**
     * Source space ID for automatic transfers
     * If null, transfers come from the main account
     * Example: 1000002 (ID of another space)
     */
    private UUID sourceSpaceId;

    /**
     * Indicates whether the space is frozen (no withdrawals or transfers allowed)
     * Default is false (not frozen)
     */
    private Boolean isFrozen;

    /**
     * Timestamp when the space was frozen
     * Null if the space has never been frozen
     */
    private LocalDateTime frozenDateTime;

    /**
     * Timestamp when the space was last unfrozen
     * Null if the space has never been unfrozen after being frozen
     */
    private LocalDateTime unfrozenDateTime;

    /**
     * Reason for the last balance update
     * Used for audit and tracking purposes
     */
    private String lastBalanceUpdateReason;

    /**
     * Timestamp of the last balance update
     */
    private LocalDateTime lastBalanceUpdateDateTime;
}
