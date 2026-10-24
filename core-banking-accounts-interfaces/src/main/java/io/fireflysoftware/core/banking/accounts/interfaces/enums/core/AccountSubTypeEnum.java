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
 * Defines the subtypes of accounts for more granular classification.
 * Each subtype provides additional specialization beyond the basic account type.
 */
public enum AccountSubTypeEnum {
    /**
     * Standard checking account with no special features
     */
    STANDARD_CHECKING,
    
    /**
     * Checking account with interest
     */
    INTEREST_CHECKING,
    
    /**
     * Checking account with rewards program
     */
    REWARDS_CHECKING,
    
    /**
     * Checking account with no monthly fees
     */
    FREE_CHECKING,
    
    /**
     * Checking account with premium features
     */
    PREMIUM_CHECKING,
    
    /**
     * Standard savings account
     */
    STANDARD_SAVINGS,
    
    /**
     * High-yield savings account
     */
    HIGH_YIELD_SAVINGS,
    
    /**
     * Money market savings account
     */
    MONEY_MARKET,
    
    /**
     * Certificate of deposit
     */
    CERTIFICATE_OF_DEPOSIT,
    
    /**
     * Individual retirement account
     */
    IRA,
    
    /**
     * Fixed-term deposit account
     */
    FIXED_TERM_DEPOSIT,
    
    /**
     * Variable-term deposit account
     */
    VARIABLE_TERM_DEPOSIT,
    
    /**
     * Personal loan account
     */
    PERSONAL_LOAN,
    
    /**
     * Auto loan account
     */
    AUTO_LOAN,
    
    /**
     * Home equity line of credit
     */
    HELOC,
    
    /**
     * Standard credit card account
     */
    STANDARD_CREDIT_CARD,
    
    /**
     * Rewards credit card account
     */
    REWARDS_CREDIT_CARD,
    
    /**
     * Cash back credit card account
     */
    CASHBACK_CREDIT_CARD,
    
    /**
     * Travel credit card account
     */
    TRAVEL_CREDIT_CARD,
    
    /**
     * Business credit card account
     */
    BUSINESS_CREDIT_CARD,
    
    /**
     * Fixed-rate mortgage
     */
    FIXED_RATE_MORTGAGE,
    
    /**
     * Adjustable-rate mortgage
     */
    ADJUSTABLE_RATE_MORTGAGE,
    
    /**
     * Interest-only mortgage
     */
    INTEREST_ONLY_MORTGAGE,
    
    /**
     * Standard business checking
     */
    STANDARD_BUSINESS_CHECKING,
    
    /**
     * Premium business checking
     */
    PREMIUM_BUSINESS_CHECKING,
    
    /**
     * Standard business savings
     */
    STANDARD_BUSINESS_SAVINGS,
    
    /**
     * Premium business savings
     */
    PREMIUM_BUSINESS_SAVINGS,
    
    /**
     * Multi-currency account
     */
    MULTI_CURRENCY,
    
    /**
     * Single currency foreign account
     */
    SINGLE_CURRENCY_FOREIGN,
    
    /**
     * Premium account with relationship benefits
     */
    RELATIONSHIP_PREMIUM,
    
    /**
     * Premium account with wealth management
     */
    WEALTH_MANAGEMENT,
    
    /**
     * Youth savings account
     */
    YOUTH_SAVINGS,
    
    /**
     * Student checking account
     */
    STUDENT_CHECKING,
    
    /**
     * Senior checking account
     */
    SENIOR_CHECKING,
    
    /**
     * Senior savings account
     */
    SENIOR_SAVINGS,
    
    /**
     * Pension distribution account
     */
    PENSION_DISTRIBUTION,
    
    /**
     * Social security benefits account
     */
    SOCIAL_SECURITY_BENEFITS,
    
    /**
     * Government assistance account
     */
    GOVERNMENT_ASSISTANCE
}
