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


package io.fireflysoftware.core.banking.accounts.interfaces.enums.space;

/**
 * Enum representing the frequency of automatic transfers between account spaces.
 */
public enum TransferFrequencyEnum {
    /**
     * Transfer occurs once per day
     */
    DAILY,
    
    /**
     * Transfer occurs once per week
     */
    WEEKLY,
    
    /**
     * Transfer occurs once per month
     */
    MONTHLY,
    
    /**
     * Transfer occurs once per quarter (every 3 months)
     */
    QUARTERLY,
    
    /**
     * Transfer occurs once per year
     */
    ANNUALLY
}