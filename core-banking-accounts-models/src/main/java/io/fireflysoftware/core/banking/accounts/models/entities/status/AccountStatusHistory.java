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


package io.fireflysoftware.core.banking.accounts.models.entities.status;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.status.StatusCodeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks the history of status changes for an account.
 * Maintains an audit trail of all status transitions for regulatory and operational purposes.
 *
 * Business Rules:
 * - Every status change must be recorded
 * - Status periods cannot overlap
 * - End datetime must be after start datetime
 * - Latest status must match Account.accountStatus
 */

@Table("account_status_history")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountStatusHistory extends BaseEntity {

    /**
     * Unique identifier for the status history record
     * Example: 1000001
     */
    @Id
    private UUID accountStatusHistoryId;

    /**
     * Reference to the associated account
     * Example: 100001 (links to Account.accountId)
     */
    private UUID accountId;

    /**
     * The status code for this period
     * Examples with typical reasons:
     * - OPEN: Normal active status
     * - SUSPENDED: Fraud investigation
     * - CLOSED: Customer request
     * - DORMANT: No activity for extended period
     */
    private StatusCodeEnum statusCode;

    /**
     * When this status became effective
     * Example: 2024-01-15T14:30:00.000Z
     */
    private LocalDateTime statusStartDatetime;

    /**
     * When this status was superseded by a new status
     * Null if this is the current status
     * Example: 2024-01-16T09:15:00.000Z
     */
    private LocalDateTime statusEndDatetime;

    /**
     * Optional explanation for the status change
     * Examples:
     * - "Customer requested account closure"
     * - "Automated dormancy after 12 months inactivity"
     * - "Fraud department investigation initiated"
     * - "Reactivated after identity verification"
     */
    private String reason;
}

