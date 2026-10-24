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


package io.fireflysoftware.core.banking.accounts.models.entities.restriction;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.restriction.RestrictionTypeEnum;
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
 * Represents a restriction or hold placed on an account.
 * Restrictions limit certain operations or access to the account.
 *
 * Business Rules:
 * - Each account can have multiple restrictions
 * - Restrictions can be time-limited or indefinite
 * - Restrictions can apply to specific amounts or the entire account
 * - Restrictions must have a reason and reference
 * - Restrictions can be applied and removed by authorized users only
 */
@Table("account_restriction")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountRestriction extends BaseEntity {

    /**
     * Unique identifier for the restriction record
     * Example: 1000001
     */
    @Id
    private UUID accountRestrictionId;

    /**
     * Reference to the associated account
     * Example: 100001 (links to Account.accountId)
     */
    private UUID accountId;

    /**
     * Type of restriction being applied
     * Examples:
     * - WITHDRAWAL_HOLD: Prevents withdrawals
     * - ACCOUNT_FREEZE: Prevents all activity
     * - LEGAL_ORDER: Restriction due to legal order
     */
    private RestrictionTypeEnum restrictionType;

    /**
     * When the restriction was applied
     * Example: 2024-01-15T14:30:00.000Z
     */
    private LocalDateTime startDateTime;

    /**
     * When the restriction will be lifted
     * Null if indefinite
     * Example: 2024-01-30T23:59:59.999Z
     */
    private LocalDateTime endDateTime;

    /**
     * Optional amount affected by the restriction
     * Null if the entire account is restricted
     * Example: 500.0000 (Only $500 is restricted)
     */
    private BigDecimal restrictedAmount;

    /**
     * Reference number for the restriction
     * Examples:
     * - "LO-2024-12345" (Legal Order reference)
     * - "FR-2024-00123" (Fraud reference)
     * - "CO-2024-54321" (Court Order reference)
     */
    private String referenceNumber;

    /**
     * Reason for the restriction
     * Examples:
     * - "Court order dated 2024-01-15"
     * - "Suspected fraudulent activity"
     * - "Customer request pending investigation"
     */
    private String reason;

    /**
     * User or system that applied the restriction
     * Examples:
     * - "john.doe" (User ID)
     * - "fraud-detection-system" (System ID)
     * - "legal-department" (Department ID)
     */
    private String appliedBy;

    /**
     * User or system that removed the restriction
     * Null if still active
     * Examples:
     * - "jane.smith" (User ID)
     * - "court-order-processor" (System ID)
     * - "branch-manager" (Role ID)
     */
    private String removedBy;

    /**
     * Notes about the restriction
     * Examples:
     * - "Customer notified by phone on 2024-01-15"
     * - "Documentation received and verified"
     * - "Pending legal department review"
     */
    private String notes;

    /**
     * Whether the restriction is currently active
     * True if active, false if removed or expired
     */
    private Boolean isActive;
}
