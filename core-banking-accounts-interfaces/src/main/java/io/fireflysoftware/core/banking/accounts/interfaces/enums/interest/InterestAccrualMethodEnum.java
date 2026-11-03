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


package io.fireflysoftware.core.banking.accounts.interfaces.enums.interest;

/**
 * Defines the methods for calculating interest accrual on accounts.
 * Different methods can result in different interest amounts for the same principal and rate.
 */
public enum InterestAccrualMethodEnum {
    /**
     * Simple interest calculation
     * Interest = Principal × Rate × Time
     */
    SIMPLE,
    
    /**
     * Daily compounding
     * Interest compounds every day
     */
    DAILY_COMPOUND,
    
    /**
     * Monthly compounding
     * Interest compounds once per month
     */
    MONTHLY_COMPOUND,
    
    /**
     * Quarterly compounding
     * Interest compounds every three months
     */
    QUARTERLY_COMPOUND,
    
    /**
     * Semi-annual compounding
     * Interest compounds twice per year
     */
    SEMI_ANNUAL_COMPOUND,
    
    /**
     * Annual compounding
     * Interest compounds once per year
     */
    ANNUAL_COMPOUND,
    
    /**
     * Continuous compounding
     * Interest compounds continuously
     */
    CONTINUOUS_COMPOUND,
    
    /**
     * Daily average balance method
     * Interest calculated on the average daily balance
     */
    DAILY_AVERAGE_BALANCE,
    
    /**
     * Monthly average balance method
     * Interest calculated on the average monthly balance
     */
    MONTHLY_AVERAGE_BALANCE,
    
    /**
     * Minimum balance method
     * Interest calculated on the minimum balance during the period
     */
    MINIMUM_BALANCE,
    
    /**
     * Tiered rate method
     * Different interest rates applied to different balance tiers
     */
    TIERED_RATE,
    
    /**
     * Stepped rate method
     * Single rate applied to entire balance based on balance tier
     */
    STEPPED_RATE,
    
    /**
     * 30/360 day count convention
     * Each month is treated as having 30 days, year as 360 days
     */
    DAY_COUNT_30_360,
    
    /**
     * Actual/360 day count convention
     * Actual days in month, year as 360 days
     */
    DAY_COUNT_ACTUAL_360,
    
    /**
     * Actual/365 day count convention
     * Actual days in month, year as 365 days
     */
    DAY_COUNT_ACTUAL_365,
    
    /**
     * Actual/Actual day count convention
     * Actual days in month, actual days in year
     */
    DAY_COUNT_ACTUAL_ACTUAL
}
