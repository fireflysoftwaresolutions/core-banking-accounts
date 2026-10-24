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


package io.fireflysoftware.core.banking.accounts.interfaces.enums.notification;

/**
 * Defines the types of notifications that can be generated for an account.
 * Notifications alert customers about various account events and conditions.
 */
public enum NotificationTypeEnum {
    /**
     * Notification for low balance
     */
    LOW_BALANCE,
    
    /**
     * Notification for large deposits
     */
    LARGE_DEPOSIT,
    
    /**
     * Notification for large withdrawals
     */
    LARGE_WITHDRAWAL,
    
    /**
     * Notification for overdrafts
     */
    OVERDRAFT,
    
    /**
     * Notification for insufficient funds
     */
    INSUFFICIENT_FUNDS,
    
    /**
     * Notification for approaching minimum balance
     */
    APPROACHING_MINIMUM_BALANCE,
    
    /**
     * Notification for statement availability
     */
    STATEMENT_AVAILABLE,
    
    /**
     * Notification for interest payment
     */
    INTEREST_PAYMENT,
    
    /**
     * Notification for fee charged
     */
    FEE_CHARGED,
    
    /**
     * Notification for account status change
     */
    STATUS_CHANGE,
    
    /**
     * Notification for approaching maturity (for term deposits)
     */
    APPROACHING_MATURITY,
    
    /**
     * Notification for maturity reached (for term deposits)
     */
    MATURITY_REACHED,
    
    /**
     * Notification for suspicious activity
     */
    SUSPICIOUS_ACTIVITY,
    
    /**
     * Notification for account restriction applied
     */
    RESTRICTION_APPLIED,
    
    /**
     * Notification for account restriction removed
     */
    RESTRICTION_REMOVED,
    
    /**
     * Notification for approaching dormancy
     */
    APPROACHING_DORMANCY,
    
    /**
     * Notification for dormancy status applied
     */
    DORMANCY_APPLIED,
    
    /**
     * Notification for goal progress (for goal-based spaces)
     */
    GOAL_PROGRESS,
    
    /**
     * Notification for goal achieved (for goal-based spaces)
     */
    GOAL_ACHIEVED,
    
    /**
     * Notification for automatic transfer executed
     */
    AUTOMATIC_TRANSFER,
    
    /**
     * Notification for automatic transfer failed
     */
    AUTOMATIC_TRANSFER_FAILED,
    
    /**
     * Notification for balance threshold reached
     */
    BALANCE_THRESHOLD,
    
    /**
     * Notification for account parameter change
     */
    PARAMETER_CHANGE,
    
    /**
     * Notification for account closure
     */
    ACCOUNT_CLOSURE
}
