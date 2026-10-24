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
 * Defines the frequency at which interest is paid on an account.
 * The frequency affects when interest is credited to the account.
 */
public enum InterestPaymentFrequencyEnum {
    /**
     * Interest paid daily
     */
    DAILY,
    
    /**
     * Interest paid weekly
     */
    WEEKLY,
    
    /**
     * Interest paid bi-weekly (every two weeks)
     */
    BI_WEEKLY,
    
    /**
     * Interest paid monthly
     */
    MONTHLY,
    
    /**
     * Interest paid quarterly (every three months)
     */
    QUARTERLY,
    
    /**
     * Interest paid semi-annually (twice per year)
     */
    SEMI_ANNUALLY,
    
    /**
     * Interest paid annually (once per year)
     */
    ANNUALLY,
    
    /**
     * Interest paid at maturity (for term deposits)
     */
    AT_MATURITY,
    
    /**
     * Interest paid at account closure
     */
    AT_CLOSURE,
    
    /**
     * Interest paid on a specific day of the month
     */
    SPECIFIC_DAY_OF_MONTH,
    
    /**
     * Interest paid on a specific date
     */
    SPECIFIC_DATE,
    
    /**
     * No interest payments (interest accrues but is not paid)
     */
    NONE
}
