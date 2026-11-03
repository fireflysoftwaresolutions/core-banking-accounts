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


package io.fireflysoftware.core.banking.accounts.interfaces.enums.core;

/**
 * Defines the types of balances that can be tracked for an account.
 * Different balance types serve different purposes in accounting and operations.
 */
public enum BalanceTypeEnum {
    /**
     * The actual balance after all cleared transactions
     */
    CURRENT,
    
    /**
     * Balance available for use (current - pending - holds)
     */
    AVAILABLE,
    
    /**
     * Amount temporarily blocked/reserved
     */
    BLOCKED,
    
    /**
     * Amount staked in a staking protocol or validator
     * Used primarily for crypto accounts with staking capabilities
     */
    STAKED,
    
    /**
     * Amount locked for a specific time period or until certain conditions are met
     * Used for time-locked tokens, vesting schedules, etc.
     */
    LOCKED,
    
    /**
     * Amount pending blockchain confirmation
     * Used for tracking deposits/withdrawals that are in-flight on the blockchain
     */
    PENDING_CONFIRMATION
}
