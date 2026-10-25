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

import io.fireflysoftware.core.banking.accounts.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a transaction that affects the balance of an account space.
 * This entity tracks all financial movements within a space.
 *
 * Business Rules:
 * - Each transaction must be linked to an account space
 * - Transactions can be deposits (positive amount) or withdrawals (negative amount)
 * - Each transaction must update the balance of the space
 * - Transactions must be timestamped
 * - Transactions can have a reference ID for cross-system tracking
 */
@Table("space_transaction")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class SpaceTransaction extends BaseEntity {

    /**
     * Unique identifier for the transaction
     * Example: 1000001
     */
    @Id
    private UUID spaceTransactionId;

    /**
     * Reference to the associated account space
     * Example: 100001 (links to AccountSpace.accountSpaceId)
     */
    private UUID accountSpaceId;

    /**
     * Transaction amount
     * Positive for deposits, negative for withdrawals
     * Examples:
     * - 100.0000 (deposit of 100)
     * - -50.0000 (withdrawal of 50)
     */
    private BigDecimal amount;

    /**
     * Balance after this transaction was applied
     * Example: 1250.0000
     */
    private BigDecimal balanceAfterTransaction;

    /**
     * When the transaction occurred
     * Example: 2024-01-15T14:30:00.000Z
     */
    @Column("transaction_datetime")
    private LocalDateTime transactionDateTime;

    /**
     * Description of the transaction
     * Examples:
     * - "Deposit from main account"
     * - "Withdrawal to vacation fund"
     * - "Automatic transfer"
     */
    private String description;

    /**
     * Optional reference ID for cross-system tracking
     * Examples:
     * - "TXN-123456" (transaction ID from another system)
     * - "TRANSFER-789" (transfer ID)
     */
    private String referenceId;

    /**
     * Type of transaction
     * Examples:
     * - "DEPOSIT"
     * - "WITHDRAWAL"
     * - "TRANSFER_IN"
     * - "TRANSFER_OUT"
     * - "INTEREST"
     * - "FEE"
     */
    private String transactionType;
}
