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


package io.fireflysoftware.core.banking.accounts.core.services.notification.impl;

import io.fireflysoftware.core.banking.accounts.core.mappers.notification.AccountNotificationMapper;
import io.fireflysoftware.core.banking.accounts.core.services.notification.AccountNotificationService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.notification.AccountNotificationDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.notification.AccountNotification;
import io.fireflysoftware.core.banking.accounts.models.repositories.notification.AccountNotificationRepository;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AccountNotificationServiceImpl implements AccountNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AccountNotificationServiceImpl.class);

    private static final String ERROR_NOTIFICATION_ID_REQUIRED = "Account notification ID is required";
    private static final String ERROR_NOTIFICATION_NOT_FOUND = "Account notification not found with ID: %s";
    private static final String ERROR_ACCOUNT_ID_REQUIRED = "Account ID is required";
    private static final String ERROR_NOTIFICATION_TYPE_REQUIRED = "Notification type is required";
    private static final String ERROR_TITLE_REQUIRED = "Title is required";
    private static final String ERROR_MESSAGE_REQUIRED = "Message is required";
    private static final String ERROR_CREATION_DATE_REQUIRED = "Creation date is required";
    private static final String ERROR_NOTIFICATION_ALREADY_READ = "Notification is already read";
    private static final String ERROR_NOTIFICATION_TYPE_INVALID = "Notification type is invalid";

    @Autowired
    private AccountNotificationRepository repository;

    @Autowired
    private AccountNotificationMapper mapper;

    @Override
    public Mono<AccountNotificationDTO> createAccountNotification(AccountNotificationDTO accountNotificationDTO) {
        // Validate required fields
        if (accountNotificationDTO.getAccountId() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }
        if (accountNotificationDTO.getNotificationType() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_NOTIFICATION_TYPE_REQUIRED));
        }
        if (accountNotificationDTO.getTitle() == null || accountNotificationDTO.getTitle().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_TITLE_REQUIRED));
        }
        if (accountNotificationDTO.getMessage() == null || accountNotificationDTO.getMessage().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_MESSAGE_REQUIRED));
        }
        if (accountNotificationDTO.getCreationDateTime() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_CREATION_DATE_REQUIRED));
        }

        // Set default values if not provided
        if (accountNotificationDTO.getIsRead() == null) {
            accountNotificationDTO.setIsRead(false);
        }
        if (accountNotificationDTO.getPriority() == null) {
            accountNotificationDTO.setPriority(3); // Default to low priority
        }

        AccountNotification accountNotification = mapper.toEntity(accountNotificationDTO);
        return repository.save(accountNotification)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountNotificationDTO> getAccountNotification(UUID accountNotificationId) {
        if (accountNotificationId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_NOTIFICATION_ID_REQUIRED));
        }

        return repository.findById(accountNotificationId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_NOTIFICATION_NOT_FOUND, accountNotificationId))));
    }

    @Override
    public Mono<AccountNotificationDTO> updateAccountNotification(UUID accountNotificationId, AccountNotificationDTO accountNotificationDTO) {
        if (accountNotificationId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_NOTIFICATION_ID_REQUIRED));
        }

        return repository.findById(accountNotificationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_NOTIFICATION_NOT_FOUND, accountNotificationId))))
                .flatMap(existingNotification -> {
                    // Update fields
                    if (accountNotificationDTO.getNotificationType() != null) {
                        existingNotification.setNotificationType(accountNotificationDTO.getNotificationType());
                    }
                    if (accountNotificationDTO.getTitle() != null) {
                        existingNotification.setTitle(accountNotificationDTO.getTitle());
                    }
                    if (accountNotificationDTO.getMessage() != null) {
                        existingNotification.setMessage(accountNotificationDTO.getMessage());
                    }
                    if (accountNotificationDTO.getCreationDateTime() != null) {
                        existingNotification.setCreationDateTime(accountNotificationDTO.getCreationDateTime());
                    }
                    if (accountNotificationDTO.getExpiryDateTime() != null) {
                        existingNotification.setExpiryDateTime(accountNotificationDTO.getExpiryDateTime());
                    }
                    if (accountNotificationDTO.getIsRead() != null) {
                        existingNotification.setIsRead(accountNotificationDTO.getIsRead());
                    }
                    if (accountNotificationDTO.getReadDateTime() != null) {
                        existingNotification.setReadDateTime(accountNotificationDTO.getReadDateTime());
                    }
                    if (accountNotificationDTO.getPriority() != null) {
                        existingNotification.setPriority(accountNotificationDTO.getPriority());
                    }
                    if (accountNotificationDTO.getDeliveryChannels() != null) {
                        existingNotification.setDeliveryChannels(accountNotificationDTO.getDeliveryChannels());
                    }
                    if (accountNotificationDTO.getEventReference() != null) {
                        existingNotification.setEventReference(accountNotificationDTO.getEventReference());
                    }
                    if (accountNotificationDTO.getRelatedAmount() != null) {
                        existingNotification.setRelatedAmount(accountNotificationDTO.getRelatedAmount());
                    }
                    if (accountNotificationDTO.getActionUrl() != null) {
                        existingNotification.setActionUrl(accountNotificationDTO.getActionUrl());
                    }
                    if (accountNotificationDTO.getActionText() != null) {
                        existingNotification.setActionText(accountNotificationDTO.getActionText());
                    }

                    return repository.save(existingNotification);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteAccountNotification(UUID accountNotificationId) {
        if (accountNotificationId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_NOTIFICATION_ID_REQUIRED));
        }

        return repository.findById(accountNotificationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_NOTIFICATION_NOT_FOUND, accountNotificationId))))
                .flatMap(notification -> repository.delete(notification));
    }

    @Override
    public Flux<AccountNotificationDTO> getAccountNotificationsByAccountId(UUID accountId) {
        if (accountId == null) {
            return Flux.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        return repository.findByAccountId(accountId)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<AccountNotificationDTO> getUnreadAccountNotifications(UUID accountId) {
        if (accountId == null) {
            return Flux.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        return repository.findByAccountIdAndIsRead(accountId, false)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<AccountNotificationDTO> getAccountNotificationsByType(UUID accountId, NotificationTypeEnum notificationType) {
        if (accountId == null) {
            return Flux.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }
        if (notificationType == null) {
            return Flux.error(new IllegalArgumentException(ERROR_NOTIFICATION_TYPE_REQUIRED));
        }

        return repository.findByAccountIdAndNotificationType(accountId, notificationType)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountNotificationDTO> markNotificationAsRead(UUID accountNotificationId) {
        if (accountNotificationId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_NOTIFICATION_ID_REQUIRED));
        }

        return repository.findById(accountNotificationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_NOTIFICATION_NOT_FOUND, accountNotificationId))))
                .flatMap(notification -> {
                    if (notification.getIsRead()) {
                        return Mono.error(new IllegalStateException(ERROR_NOTIFICATION_ALREADY_READ));
                    }

                    notification.setIsRead(true);
                    notification.setReadDateTime(LocalDateTime.now());

                    return repository.save(notification);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Long> markAllNotificationsAsRead(UUID accountId) {
        if (accountId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        LocalDateTime now = LocalDateTime.now();

        return repository.findByAccountIdAndIsRead(accountId, false)
                .flatMap(notification -> {
                    notification.setIsRead(true);
                    notification.setReadDateTime(now);
                    return repository.save(notification);
                })
                .count();
    }

    @Override
    public Flux<AccountNotificationDTO> getActiveNotifications(UUID accountId) {
        if (accountId == null) {
            return Flux.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        LocalDateTime now = LocalDateTime.now();

        return repository.findByAccountIdAndExpiryDateTimeIsNullOrExpiryDateTimeGreaterThan(accountId, now)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<AccountNotificationDTO>> listAccountNotifications(FilterRequest filterRequest) {
        return FilterUtils
                .createFilter(
                        AccountNotification.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }
}
