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


package io.fireflysoftware.core.banking.accounts.core.services.status.impl;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.banking.accounts.core.mappers.status.AccountStatusHistoryMapper;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.status.AccountStatusHistoryDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.status.StatusCodeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.status.AccountStatusHistory;
import io.fireflysoftware.core.banking.accounts.models.repositories.status.AccountStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AccountStatusHistoryServiceImplTest {

    @Mock
    private AccountStatusHistoryRepository repository;

    @Mock
    private AccountStatusHistoryMapper mapper;

    @InjectMocks
    private AccountStatusHistoryServiceImpl accountStatusHistoryService;

    private AccountStatusHistory testAccountStatusHistory;
    private AccountStatusHistoryDTO testAccountStatusHistoryDTO;
    private final UUID TEST_ACCOUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440063");
    private final UUID TEST_HISTORY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440064");

    @BeforeEach
    void setUp() {
        // Setup test account status history entity
        testAccountStatusHistory = new AccountStatusHistory();
        testAccountStatusHistory.setAccountStatusHistoryId(TEST_HISTORY_ID);
        testAccountStatusHistory.setAccountId(TEST_ACCOUNT_ID);
        testAccountStatusHistory.setStatusCode(StatusCodeEnum.OPEN);
        testAccountStatusHistory.setStatusStartDatetime(LocalDateTime.now().minusDays(30));
        testAccountStatusHistory.setStatusEndDatetime(null); // Current status has no end date
        testAccountStatusHistory.setReason("Account opened");

        // Setup test account status history DTO
        testAccountStatusHistoryDTO = AccountStatusHistoryDTO.builder()
                .accountStatusHistoryId(TEST_HISTORY_ID)
                .accountId(TEST_ACCOUNT_ID)
                .statusCode(StatusCodeEnum.OPEN)
                .statusStartDatetime(LocalDateTime.now().minusDays(30))
                .statusEndDatetime(null)
                .reason("Account opened")
                .dateCreated(LocalDateTime.now().minusDays(30))
                .dateUpdated(LocalDateTime.now().minusDays(30))
                .build();
    }

    @Test
    void listStatusHistory_ShouldReturnPaginatedStatusHistory() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest();
        
        // Create a mock PaginationResponse
        @SuppressWarnings("unchecked")
        PaginationResponse<AccountStatusHistoryDTO> mockResponse = mock(PaginationResponse.class);
        
        // Mock the static PaginationUtils.paginateQuery method
        try (MockedStatic<PaginationUtils> mockedPaginationUtils = mockStatic(PaginationUtils.class)) {
            mockedPaginationUtils.when(() -> PaginationUtils.paginateQuery(
                    any(PaginationRequest.class),
                    any(Function.class),
                    any(Function.class),
                    any(Supplier.class)
            )).thenReturn(Mono.just(mockResponse));
            
            // Act & Assert
            StepVerifier.create(accountStatusHistoryService.listStatusHistory(TEST_ACCOUNT_ID, paginationRequest))
                    .expectNext(mockResponse)
                    .verifyComplete();
                    
            // Verify the PaginationUtils.paginateQuery was called
            mockedPaginationUtils.verify(() -> PaginationUtils.paginateQuery(
                    eq(paginationRequest),
                    any(Function.class),
                    any(Function.class),
                    any(Supplier.class)
            ));
        }
    }

    @Test
    void createStatusHistory_ShouldReturnCreatedStatusHistory() {
        // Arrange
        when(mapper.toEntity(any(AccountStatusHistoryDTO.class))).thenReturn(testAccountStatusHistory);
        when(repository.save(any(AccountStatusHistory.class))).thenReturn(Mono.just(testAccountStatusHistory));
        when(mapper.toDTO(any(AccountStatusHistory.class))).thenReturn(testAccountStatusHistoryDTO);

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.createStatusHistory(TEST_ACCOUNT_ID, testAccountStatusHistoryDTO))
                .expectNext(testAccountStatusHistoryDTO)
                .verifyComplete();

        verify(mapper).toEntity(testAccountStatusHistoryDTO);
        verify(repository).save(testAccountStatusHistory);
        verify(mapper).toDTO(testAccountStatusHistory);
    }

    @Test
    void getStatusHistory_ShouldReturnStatusHistory_WhenHistoryExistsForAccount() {
        // Arrange
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.just(testAccountStatusHistory));
        when(mapper.toDTO(testAccountStatusHistory)).thenReturn(testAccountStatusHistoryDTO);

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.getStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID))
                .expectNext(testAccountStatusHistoryDTO)
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verify(mapper).toDTO(testAccountStatusHistory);
    }

    @Test
    void getStatusHistory_ShouldReturnEmptyMono_WhenHistoryDoesNotExistForAccount() {
        // Arrange
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.getStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void getStatusHistory_ShouldReturnEmptyMono_WhenHistoryExistsButNotForSpecifiedAccount() {
        // Arrange
        AccountStatusHistory historyForDifferentAccount = new AccountStatusHistory();
        historyForDifferentAccount.setAccountStatusHistoryId(TEST_HISTORY_ID);
        historyForDifferentAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440065")); // Different account ID
        
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.just(historyForDifferentAccount));

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.getStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void updateStatusHistory_ShouldReturnUpdatedStatusHistory_WhenHistoryExistsForAccount() {
        // Arrange
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.just(testAccountStatusHistory));
        when(mapper.toEntity(testAccountStatusHistoryDTO)).thenReturn(testAccountStatusHistory);
        when(repository.save(testAccountStatusHistory)).thenReturn(Mono.just(testAccountStatusHistory));
        when(mapper.toDTO(testAccountStatusHistory)).thenReturn(testAccountStatusHistoryDTO);

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.updateStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID, testAccountStatusHistoryDTO))
                .expectNext(testAccountStatusHistoryDTO)
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verify(mapper).toEntity(testAccountStatusHistoryDTO);
        verify(repository).save(testAccountStatusHistory);
        verify(mapper).toDTO(testAccountStatusHistory);
    }

    @Test
    void updateStatusHistory_ShouldReturnEmptyMono_WhenHistoryDoesNotExistForAccount() {
        // Arrange
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.updateStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID, testAccountStatusHistoryDTO))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verifyNoMoreInteractions(mapper, repository);
    }

    @Test
    void updateStatusHistory_ShouldReturnEmptyMono_WhenHistoryExistsButNotForSpecifiedAccount() {
        // Arrange
        AccountStatusHistory historyForDifferentAccount = new AccountStatusHistory();
        historyForDifferentAccount.setAccountStatusHistoryId(TEST_HISTORY_ID);
        historyForDifferentAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440066")); // Different account ID
        
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.just(historyForDifferentAccount));

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.updateStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID, testAccountStatusHistoryDTO))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verifyNoMoreInteractions(mapper, repository);
    }

    @Test
    void deleteStatusHistory_ShouldDeleteStatusHistory_WhenHistoryExistsForAccount() {
        // Arrange
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.just(testAccountStatusHistory));
        when(repository.delete(testAccountStatusHistory)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.deleteStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verify(repository).delete(testAccountStatusHistory);
    }

    @Test
    void deleteStatusHistory_ShouldReturnEmptyMono_WhenHistoryDoesNotExistForAccount() {
        // Arrange
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.deleteStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteStatusHistory_ShouldReturnEmptyMono_WhenHistoryExistsButNotForSpecifiedAccount() {
        // Arrange
        AccountStatusHistory historyForDifferentAccount = new AccountStatusHistory();
        historyForDifferentAccount.setAccountStatusHistoryId(TEST_HISTORY_ID);
        historyForDifferentAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440067")); // Different account ID
        
        when(repository.findById(TEST_HISTORY_ID)).thenReturn(Mono.just(historyForDifferentAccount));

        // Act & Assert
        StepVerifier.create(accountStatusHistoryService.deleteStatusHistory(TEST_ACCOUNT_ID, TEST_HISTORY_ID))
                .verifyComplete();

        verify(repository).findById(TEST_HISTORY_ID);
        verifyNoMoreInteractions(repository);
    }
}