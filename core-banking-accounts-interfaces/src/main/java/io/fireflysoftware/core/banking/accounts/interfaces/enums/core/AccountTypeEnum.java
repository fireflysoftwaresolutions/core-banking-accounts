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
 * Defines the types of accounts supported by the system.
 * Each type has different characteristics, rules, and parameters.
 */
public enum AccountTypeEnum {
    /**
     * Standard checking account for everyday transactions
     */
    CHECKING,
    
    /**
     * Interest-bearing account for saving money
     */
    SAVINGS,
    
    /**
     * Account for fixed-term deposits with higher interest rates
     */
    TERM_DEPOSIT,
    
    /**
     * Credit account for loans
     */
    LOAN,
    
    /**
     * Account for credit card transactions
     */
    CREDIT_CARD,
    
    /**
     * Investment account for securities
     */
    INVESTMENT,
    
    /**
     * Account for mortgage loans
     */
    MORTGAGE,
    
    /**
     * Business checking account
     */
    BUSINESS_CHECKING,
    
    /**
     * Business savings account
     */
    BUSINESS_SAVINGS,
    
    /**
     * Account for foreign currency
     */
    FOREIGN_CURRENCY,
    
    /**
     * Account with special features for high-value customers
     */
    PREMIUM,
    
    /**
     * Account for youth or students
     */
    YOUTH,
    
    /**
     * Account for senior citizens
     */
    SENIOR,
    
    /**
     * Account for pension payments
     */
    PENSION,
    
    /**
     * Account for government benefits
     */
    GOVERNMENT_BENEFITS,
    
    /**
     * Account for cryptocurrency storage and transactions
     */
    CRYPTO_WALLET,
    
    /**
     * Account for tokenized traditional assets
     */
    TOKENIZED_ASSET,
    
    /**
     * Account for general digital assets
     */
    DIGITAL_ASSET,
    
    /**
     * Account for stablecoins (cryptocurrencies pegged to fiat)
     */
    STABLECOIN,
    
    /**
     * Account for non-fungible token collections
     */
    NFT_COLLECTION
}