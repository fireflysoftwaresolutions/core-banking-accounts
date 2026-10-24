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


package io.fireflysoftware.core.banking.accounts.interfaces.enums.restriction;

/**
 * Defines the types of restrictions that can be applied to an account.
 * Restrictions limit certain operations or access to the account.
 */
public enum RestrictionTypeEnum {
    /**
     * Temporary hold on withdrawals
     */
    WITHDRAWAL_HOLD,
    
    /**
     * Temporary hold on deposits
     */
    DEPOSIT_HOLD,
    
    /**
     * Complete freeze on all account activity
     */
    ACCOUNT_FREEZE,
    
    /**
     * Restriction due to legal order
     */
    LEGAL_ORDER,
    
    /**
     * Restriction due to court order
     */
    COURT_ORDER,
    
    /**
     * Restriction due to suspected fraud
     */
    FRAUD_INVESTIGATION,
    
    /**
     * Restriction due to deceased account holder
     */
    DECEASED_HOLDER,
    
    /**
     * Restriction due to dormant account status
     */
    DORMANCY,
    
    /**
     * Restriction due to negative balance
     */
    NEGATIVE_BALANCE,
    
    /**
     * Restriction due to excessive overdrafts
     */
    EXCESSIVE_OVERDRAFTS,
    
    /**
     * Restriction due to suspicious activity
     */
    SUSPICIOUS_ACTIVITY,
    
    /**
     * Restriction due to anti-money laundering investigation
     */
    AML_INVESTIGATION,
    
    /**
     * Restriction on international transfers
     */
    INTERNATIONAL_TRANSFER_RESTRICTION,
    
    /**
     * Restriction on high-value transactions
     */
    HIGH_VALUE_TRANSACTION_RESTRICTION,
    
    /**
     * Restriction due to pending documentation
     */
    PENDING_DOCUMENTATION,
    
    /**
     * Restriction due to expired identification
     */
    EXPIRED_IDENTIFICATION,
    
    /**
     * Restriction due to bankruptcy proceedings
     */
    BANKRUPTCY,
    
    /**
     * Restriction due to estate settlement
     */
    ESTATE_SETTLEMENT,
    
    /**
     * Restriction due to regulatory compliance
     */
    REGULATORY_COMPLIANCE,
    
    /**
     * Restriction due to sanctions screening
     */
    SANCTIONS_SCREENING,
    
    /**
     * Restriction on specific transaction types
     */
    TRANSACTION_TYPE_RESTRICTION,
    
    /**
     * Restriction on specific channels (e.g., online, mobile)
     */
    CHANNEL_RESTRICTION,
    
    /**
     * Restriction on geographic regions
     */
    GEOGRAPHIC_RESTRICTION,
    
    /**
     * Restriction due to account closure in progress
     */
    CLOSURE_IN_PROGRESS
}
