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


package io.fireflysoftware.core.banking.accounts.core.services.notification;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.notification.AccountNotificationDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountNotificationService {
    
    /**
     * Create a new account notification
     * @param accountNotificationDTO The account notification DTO
     * @return Mono of AccountNotificationDTO
     */
    Mono<AccountNotificationDTO> createAccountNotification(AccountNotificationDTO accountNotificationDTO);
    
    /**
     * Get an account notification by ID
     * @param accountNotificationId The account notification ID
     * @return Mono of AccountNotificationDTO
     */
    Mono<AccountNotificationDTO> getAccountNotification(UUID accountNotificationId);
    
    /**
     * Update an account notification
     * @param accountNotificationId The account notification ID
     * @param accountNotificationDTO The account notification DTO
     * @return Mono of AccountNotificationDTO
     */
    Mono<AccountNotificationDTO> updateAccountNotification(UUID accountNotificationId, AccountNotificationDTO accountNotificationDTO);
    
    /**
     * Delete an account notification
     * @param accountNotificationId The account notification ID
     * @return Mono of Void
     */
    Mono<Void> deleteAccountNotification(UUID accountNotificationId);
    
    /**
     * Get all account notifications for an account
     * @param accountId The account ID
     * @return Flux of AccountNotificationDTO
     */
    Flux<AccountNotificationDTO> getAccountNotificationsByAccountId(UUID accountId);
    
    /**
     * Get unread account notifications for an account
     * @param accountId The account ID
     * @return Flux of AccountNotificationDTO
     */
    Flux<AccountNotificationDTO> getUnreadAccountNotifications(UUID accountId);
    
    /**
     * Get account notifications by type for an account
     * @param accountId The account ID
     * @param notificationType The notification type
     * @return Flux of AccountNotificationDTO
     */
    Flux<AccountNotificationDTO> getAccountNotificationsByType(UUID accountId, NotificationTypeEnum notificationType);
    
    /**
     * Mark an account notification as read
     * @param accountNotificationId The account notification ID
     * @return Mono of AccountNotificationDTO
     */
    Mono<AccountNotificationDTO> markNotificationAsRead(UUID accountNotificationId);
    
    /**
     * Mark all notifications as read for an account
     * @param accountId The account ID
     * @return Mono of Long representing the number of notifications marked as read
     */
    Mono<Long> markAllNotificationsAsRead(UUID accountId);
    
    /**
     * Get active (non-expired) notifications for an account
     * @param accountId The account ID
     * @return Flux of AccountNotificationDTO
     */
    Flux<AccountNotificationDTO> getActiveNotifications(UUID accountId);
    
    /**
     * List account notifications with pagination and filtering
     * @param filterRequest The filter request
     * @return Mono of PaginationResponse containing AccountNotificationDTO
     */
    Mono<PaginationResponse<AccountNotificationDTO>> listAccountNotifications(FilterRequest filterRequest);
}
