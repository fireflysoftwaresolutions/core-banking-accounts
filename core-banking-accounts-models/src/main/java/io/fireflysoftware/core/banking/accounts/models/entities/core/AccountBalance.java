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


package io.fireflysoftware.core.banking.accounts.models.entities.core;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.BalanceTypeEnum;
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
 * Represents the balance information for an account.
 * Tracks different types of balances (current, available, blocked) for each account and account space.
 *
 * Business Rules:
 * - Each account can have multiple balance types
 * - Each account space can have multiple balance types
 * - Balance amounts must be stored with 4 decimal places
 * - Historical balance records should be maintained
 * - Balance updates must be timestamped
 * - If accountSpaceId is null, the balance is for the global account
 * - The sum of all space balances must equal the account's global balance
 */
@Table("account_balance")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountBalance extends BaseEntity {

    /**
     * Unique identifier for the balance record
     * Example: 1000001
     */
    @Id
    private UUID accountBalanceId;

    /**
     * Reference to the associated account
     * Example: 100001 (links to Account.accountId)
     */
    private UUID accountId;

    /**
     * Optional reference to the associated account space
     * If null, this balance is for the global account (not a specific space)
     * Example: 100001 (links to AccountSpace.accountSpaceId)
     */
    private UUID accountSpaceId;

    /**
     * Type of balance being recorded
     * CURRENT: Actual balance after all cleared transactions
     * AVAILABLE: Balance available for use (current - pending - holds)
     * BLOCKED: Amount temporarily blocked/reserved
     */
    private BalanceTypeEnum balanceType;

    /**
     * The actual balance amount
     * Stored with 4 decimal places for high precision
     * Examples:
     * - 1000.0000 (One thousand)
     * - -50.5000 (Negative fifty and fifty cents)
     * - 0.0001 (Smallest unit)
     */
    private BigDecimal balanceAmount;

    /**
     * Timestamp when this balance was recorded
     * Used for:
     * - Historical tracking
     * - Reconciliation
     * - Reporting
     * Example: 2024-01-15T14:30:00.000Z
     */
    private LocalDateTime asOfDatetime;

    /**
     * Symbol or ticker of the crypto asset
     * Examples:
     * - "BTC" - Bitcoin
     * - "ETH" - Ethereum
     * - "USDC" - USD Coin
     * - "SOL" - Solana
     * Null for traditional fiat currency accounts
     */
    private String assetSymbol;

    /**
     * Number of decimal places used by the token
     * Different tokens have different decimal precision
     * Examples:
     * - "8" for Bitcoin (1 BTC = 100,000,000 satoshis)
     * - "18" for Ethereum (1 ETH = 10^18 wei)
     * - "6" for USDC (1 USDC = 1,000,000 microUSDC)
     * Null for traditional fiat currency accounts
     */
    private String assetDecimals;

    /**
     * Blockchain transaction hash/ID that affected this balance
     * Used for:
     * - Transaction verification
     * - Blockchain reconciliation
     * - Audit trail
     * Examples:
     * - Bitcoin: "3a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u"
     * - Ethereum: "0x742d35Cc6634C0532925a3b844Bc454e4438f44e123456789abcdef0123456789"
     * Null for traditional banking transactions or pending crypto transactions
     */
    private String transactionHash;

    /**
     * Number of blockchain confirmations for the transaction
     * Used to determine finality of crypto transactions
     * Examples:
     * - 0: Transaction is in mempool/pending
     * - 1-5: Transaction is confirmed but not fully secure
     * - 6+: Transaction is considered final (for Bitcoin)
     * - 12+: Transaction is considered final (for Ethereum)
     * Null for traditional banking transactions
     */
    private Integer confirmations;
}
