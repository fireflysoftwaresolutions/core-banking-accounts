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


package io.fireflysoftware.core.banking.accounts.models.repositories.notification;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.notification.AccountNotification;
import io.fireflysoftware.core.banking.accounts.models.repositories.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AccountNotificationRepository extends BaseRepository<AccountNotification, UUID> {
    
    /**
     * Find all notifications for a specific account
     * @param accountId The account ID
     * @return Flux of AccountNotification
     */
    Flux<AccountNotification> findByAccountId(UUID accountId);
    
    /**
     * Find unread notifications for a specific account
     * @param accountId The account ID
     * @param isRead Whether the notification has been read
     * @return Flux of AccountNotification
     */
    Flux<AccountNotification> findByAccountIdAndIsRead(UUID accountId, Boolean isRead);
    
    /**
     * Find notifications by type for a specific account
     * @param accountId The account ID
     * @param notificationType The notification type
     * @return Flux of AccountNotification
     */
    Flux<AccountNotification> findByAccountIdAndNotificationType(UUID accountId, NotificationTypeEnum notificationType);
    
    /**
     * Find notifications by priority for a specific account
     * @param accountId The account ID
     * @param priority The priority level
     * @return Flux of AccountNotification
     */
    Flux<AccountNotification> findByAccountIdAndPriority(UUID accountId, Integer priority);
    
    /**
     * Count unread notifications for a specific account
     * @param accountId The account ID
     * @param isRead Whether the notification has been read
     * @return Mono of Long representing the count
     */
    Mono<Long> countByAccountIdAndIsRead(UUID accountId, Boolean isRead);
    
    /**
     * Find notifications that have not expired
     * @param accountId The account ID
     * @param currentDateTime The current date and time
     * @return Flux of AccountNotification
     */
    Flux<AccountNotification> findByAccountIdAndExpiryDateTimeIsNullOrExpiryDateTimeGreaterThan(
            UUID accountId, LocalDateTime currentDateTime);
}
