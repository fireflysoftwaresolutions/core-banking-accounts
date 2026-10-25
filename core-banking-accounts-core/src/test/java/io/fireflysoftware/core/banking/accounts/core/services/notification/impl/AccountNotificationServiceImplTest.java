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
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.notification.AccountNotificationDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.notification.AccountNotification;
import io.fireflysoftware.core.banking.accounts.models.repositories.notification.AccountNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AccountNotificationServiceImplTest {

    @Mock
    private AccountNotificationRepository repository;

    @Mock
    private AccountNotificationMapper mapper;

    @InjectMocks
    private AccountNotificationServiceImpl service;

    private AccountNotification accountNotification;
    private AccountNotificationDTO accountNotificationDTO;
    private final UUID NOTIFICATION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440009");
    private final UUID ACCOUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    @BeforeEach
    void setUp() {
        // Setup test data
        accountNotification = new AccountNotification();
        accountNotification.setAccountNotificationId(NOTIFICATION_ID);
        accountNotification.setAccountId(ACCOUNT_ID);
        accountNotification.setNotificationType(NotificationTypeEnum.LOW_BALANCE);
        accountNotification.setTitle("Low Balance Alert");
        accountNotification.setMessage("Your account balance is below $100");
        accountNotification.setCreationDateTime(LocalDateTime.now());
        accountNotification.setPriority(1);
        accountNotification.setIsRead(false);
        accountNotification.setRelatedAmount(BigDecimal.valueOf(95.50));

        accountNotificationDTO = new AccountNotificationDTO();
        accountNotificationDTO.setAccountNotificationId(NOTIFICATION_ID);
        accountNotificationDTO.setAccountId(ACCOUNT_ID);
        accountNotificationDTO.setNotificationType(NotificationTypeEnum.LOW_BALANCE);
        accountNotificationDTO.setTitle("Low Balance Alert");
        accountNotificationDTO.setMessage("Your account balance is below $100");
        accountNotificationDTO.setCreationDateTime(LocalDateTime.now());
        accountNotificationDTO.setPriority(1);
        accountNotificationDTO.setIsRead(false);
        accountNotificationDTO.setRelatedAmount(BigDecimal.valueOf(95.50));
    }

    @Test
    void createAccountNotification_Success() {
        // Arrange
        when(mapper.toEntity(any(AccountNotificationDTO.class))).thenReturn(accountNotification);
        when(repository.save(any(AccountNotification.class))).thenReturn(Mono.just(accountNotification));
        when(mapper.toDTO(any(AccountNotification.class))).thenReturn(accountNotificationDTO);

        // Act & Assert
        StepVerifier.create(service.createAccountNotification(accountNotificationDTO))
                .expectNext(accountNotificationDTO)
                .verifyComplete();

        verify(repository).save(any(AccountNotification.class));
    }

    @Test
    void createAccountNotification_MissingAccountId() {
        // Arrange
        accountNotificationDTO.setAccountId(null);

        // Act & Assert
        StepVerifier.create(service.createAccountNotification(accountNotificationDTO))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(repository, never()).save(any(AccountNotification.class));
    }

    @Test
    void getAccountNotification_Success() {
        // Arrange
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Mono.just(accountNotification));
        when(mapper.toDTO(accountNotification)).thenReturn(accountNotificationDTO);

        // Act & Assert
        StepVerifier.create(service.getAccountNotification(NOTIFICATION_ID))
                .expectNext(accountNotificationDTO)
                .verifyComplete();

        verify(repository).findById(NOTIFICATION_ID);
    }

    @Test
    void getAccountNotification_NotFound() {
        // Arrange
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.getAccountNotification(NOTIFICATION_ID))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(repository).findById(NOTIFICATION_ID);
    }

    @Test
    void updateAccountNotification_Success() {
        // Arrange
        AccountNotification updatedNotification = new AccountNotification();
        updatedNotification.setAccountNotificationId(NOTIFICATION_ID);
        updatedNotification.setMessage("Updated message");

        AccountNotificationDTO updatedDTO = new AccountNotificationDTO();
        updatedDTO.setAccountNotificationId(NOTIFICATION_ID);
        updatedDTO.setMessage("Updated message");

        when(repository.findById(NOTIFICATION_ID)).thenReturn(Mono.just(accountNotification));
        when(repository.save(any(AccountNotification.class))).thenReturn(Mono.just(updatedNotification));
        when(mapper.toDTO(updatedNotification)).thenReturn(updatedDTO);

        // Act & Assert
        StepVerifier.create(service.updateAccountNotification(NOTIFICATION_ID, updatedDTO))
                .expectNext(updatedDTO)
                .verifyComplete();

        verify(repository).findById(NOTIFICATION_ID);
        verify(repository).save(any(AccountNotification.class));
    }

    @Test
    void deleteAccountNotification_Success() {
        // Arrange
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Mono.just(accountNotification));
        when(repository.delete(accountNotification)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(service.deleteAccountNotification(NOTIFICATION_ID))
                .verifyComplete();

        verify(repository).findById(NOTIFICATION_ID);
        verify(repository).delete(accountNotification);
    }

    @Test
    void getAccountNotificationsByAccountId_Success() {
        // Arrange
        when(repository.findByAccountId(ACCOUNT_ID)).thenReturn(Flux.just(accountNotification));
        when(mapper.toDTO(accountNotification)).thenReturn(accountNotificationDTO);

        // Act & Assert
        StepVerifier.create(service.getAccountNotificationsByAccountId(ACCOUNT_ID))
                .expectNext(accountNotificationDTO)
                .verifyComplete();

        verify(repository).findByAccountId(ACCOUNT_ID);
    }

    @Test
    void getUnreadAccountNotifications_Success() {
        // Arrange
        when(repository.findByAccountIdAndIsRead(ACCOUNT_ID, false)).thenReturn(Flux.just(accountNotification));
        when(mapper.toDTO(accountNotification)).thenReturn(accountNotificationDTO);

        // Act & Assert
        StepVerifier.create(service.getUnreadAccountNotifications(ACCOUNT_ID))
                .expectNext(accountNotificationDTO)
                .verifyComplete();

        verify(repository).findByAccountIdAndIsRead(ACCOUNT_ID, false);
    }

    @Test
    void getAccountNotificationsByType_Success() {
        // Arrange
        NotificationTypeEnum notificationType = NotificationTypeEnum.LOW_BALANCE;

        when(repository.findByAccountIdAndNotificationType(ACCOUNT_ID, notificationType))
                .thenReturn(Flux.just(accountNotification));
        when(mapper.toDTO(accountNotification)).thenReturn(accountNotificationDTO);

        // Act & Assert
        StepVerifier.create(service.getAccountNotificationsByType(ACCOUNT_ID, notificationType))
                .expectNext(accountNotificationDTO)
                .verifyComplete();

        verify(repository).findByAccountIdAndNotificationType(ACCOUNT_ID, notificationType);
    }

    @Test
    void markNotificationAsRead_Success() {
        // Arrange
        AccountNotification readNotification = new AccountNotification();
        readNotification.setAccountNotificationId(NOTIFICATION_ID);
        readNotification.setIsRead(true);
        // Don't use matchers in object initialization
        LocalDateTime readTime = LocalDateTime.now();
        readNotification.setReadDateTime(readTime);

        AccountNotificationDTO readDTO = new AccountNotificationDTO();
        readDTO.setAccountNotificationId(NOTIFICATION_ID);
        readDTO.setIsRead(true);
        readDTO.setReadDateTime(readTime);

        when(repository.findById(NOTIFICATION_ID)).thenReturn(Mono.just(accountNotification));
        when(repository.save(any(AccountNotification.class))).thenReturn(Mono.just(readNotification));
        when(mapper.toDTO(readNotification)).thenReturn(readDTO);

        // Act & Assert
        StepVerifier.create(service.markNotificationAsRead(NOTIFICATION_ID))
                .expectNext(readDTO)
                .verifyComplete();

        verify(repository).findById(NOTIFICATION_ID);
        verify(repository).save(any(AccountNotification.class));
    }

    @Test
    void markNotificationAsRead_AlreadyRead() {
        // Arrange
        accountNotification.setIsRead(true);

        when(repository.findById(NOTIFICATION_ID)).thenReturn(Mono.just(accountNotification));

        // Act & Assert
        StepVerifier.create(service.markNotificationAsRead(NOTIFICATION_ID))
                .expectError(IllegalStateException.class)
                .verify();

        verify(repository).findById(NOTIFICATION_ID);
        verify(repository, never()).save(any(AccountNotification.class));
    }

    @Test
    void markAllNotificationsAsRead_Success() {
        // Arrange
        AccountNotification notification1 = new AccountNotification();
        notification1.setAccountNotificationId(UUID.fromString("550e8400-e29b-41d4-a716-446655440050"));
        notification1.setAccountId(ACCOUNT_ID);
        notification1.setIsRead(false);

        AccountNotification notification2 = new AccountNotification();
        notification2.setAccountNotificationId(UUID.fromString("550e8400-e29b-41d4-a716-446655440051"));
        notification2.setAccountId(ACCOUNT_ID);
        notification2.setIsRead(false);

        AccountNotification readNotification1 = new AccountNotification();
        readNotification1.setAccountNotificationId(UUID.fromString("550e8400-e29b-41d4-a716-446655440050"));
        readNotification1.setIsRead(true);

        AccountNotification readNotification2 = new AccountNotification();
        readNotification2.setAccountNotificationId(UUID.fromString("550e8400-e29b-41d4-a716-446655440051"));
        readNotification2.setIsRead(true);

        when(repository.findByAccountIdAndIsRead(ACCOUNT_ID, false))
                .thenReturn(Flux.just(notification1, notification2));
        when(repository.save(any(AccountNotification.class)))
                .thenReturn(Mono.just(readNotification1))
                .thenReturn(Mono.just(readNotification2));

        // Act & Assert
        StepVerifier.create(service.markAllNotificationsAsRead(ACCOUNT_ID))
                .expectNext(2L)
                .verifyComplete();

        verify(repository).findByAccountIdAndIsRead(ACCOUNT_ID, false);
        verify(repository, times(2)).save(any(AccountNotification.class));
    }

    @Test
    void getActiveNotifications_Success() {
        // Arrange
        when(repository.findByAccountIdAndExpiryDateTimeIsNullOrExpiryDateTimeGreaterThan(eq(ACCOUNT_ID), any(LocalDateTime.class)))
                .thenReturn(Flux.just(accountNotification));
        when(mapper.toDTO(accountNotification)).thenReturn(accountNotificationDTO);

        // Act & Assert
        StepVerifier.create(service.getActiveNotifications(ACCOUNT_ID))
                .expectNext(accountNotificationDTO)
                .verifyComplete();

        verify(repository).findByAccountIdAndExpiryDateTimeIsNullOrExpiryDateTimeGreaterThan(eq(ACCOUNT_ID), any(LocalDateTime.class));
    }
}
