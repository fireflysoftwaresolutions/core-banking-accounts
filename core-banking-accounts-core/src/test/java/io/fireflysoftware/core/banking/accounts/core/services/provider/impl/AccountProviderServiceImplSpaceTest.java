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


package io.fireflysoftware.core.banking.accounts.core.services.provider.impl;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.banking.accounts.core.mappers.provider.AccountProviderMapper;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.provider.AccountProviderDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.provider.ProviderStatusEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.provider.AccountProvider;
import io.fireflysoftware.core.banking.accounts.models.repositories.provider.AccountProviderRepository;
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
class AccountProviderServiceImplSpaceTest {

    @Mock
    private AccountProviderRepository repository;

    @Mock
    private AccountProviderMapper mapper;

    @InjectMocks
    private AccountProviderServiceImpl accountProviderService;

    private AccountProvider testAccountProvider;
    private AccountProviderDTO testAccountProviderDTO;

    private final UUID TEST_ACCOUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");
    private final UUID TEST_ACCOUNT_SPACE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440005");
    private final UUID TEST_PROVIDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440006");

    @BeforeEach
    void setUp() {
        testAccountProvider = new AccountProvider();
        testAccountProvider.setAccountProviderId(TEST_PROVIDER_ID);
        testAccountProvider.setAccountId(TEST_ACCOUNT_ID);
        testAccountProvider.setAccountSpaceId(TEST_ACCOUNT_SPACE_ID);
        testAccountProvider.setProviderName("Test Provider");
        testAccountProvider.setExternalReference("TEST-REF-001");
        testAccountProvider.setStatus(ProviderStatusEnum.ACTIVE);

        testAccountProviderDTO = new AccountProviderDTO();
        testAccountProviderDTO.setAccountProviderId(TEST_PROVIDER_ID);
        testAccountProviderDTO.setAccountId(TEST_ACCOUNT_ID);
        testAccountProviderDTO.setAccountSpaceId(TEST_ACCOUNT_SPACE_ID);
        testAccountProviderDTO.setProviderName("Test Provider");
        testAccountProviderDTO.setExternalReference("TEST-REF-001");
        testAccountProviderDTO.setStatus(ProviderStatusEnum.ACTIVE);
        testAccountProviderDTO.setDateCreated(LocalDateTime.now());
        testAccountProviderDTO.setDateUpdated(LocalDateTime.now());
    }

    @Test
    void listProvidersForSpace_ShouldReturnPaginatedProviders() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest();

        // Create a mock PaginationResponse
        @SuppressWarnings("unchecked")
        PaginationResponse<AccountProviderDTO> mockResponse = mock(PaginationResponse.class);

        // Mock the static PaginationUtils.paginateQuery method
        try (MockedStatic<PaginationUtils> mockedPaginationUtils = mockStatic(PaginationUtils.class)) {
            mockedPaginationUtils.when(() -> PaginationUtils.paginateQuery(
                    any(PaginationRequest.class),
                    any(Function.class),
                    any(Function.class),
                    any(Supplier.class)
            )).thenReturn(Mono.just(mockResponse));

            // Act & Assert
            StepVerifier.create(accountProviderService.listProvidersForSpace(TEST_ACCOUNT_ID, TEST_ACCOUNT_SPACE_ID, paginationRequest))
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
    void createProviderForSpace_ShouldReturnCreatedProvider() {
        // Arrange
        when(mapper.toEntity(any(AccountProviderDTO.class))).thenReturn(testAccountProvider);
        when(repository.save(any(AccountProvider.class))).thenReturn(Mono.just(testAccountProvider));
        when(mapper.toDTO(any(AccountProvider.class))).thenReturn(testAccountProviderDTO);

        // Act
        Mono<AccountProviderDTO> result = accountProviderService.createProviderForSpace(
                TEST_ACCOUNT_ID, TEST_ACCOUNT_SPACE_ID, testAccountProviderDTO);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto -> 
                        dto.getAccountProviderId().equals(TEST_PROVIDER_ID) &&
                        dto.getAccountId().equals(TEST_ACCOUNT_ID) &&
                        dto.getAccountSpaceId().equals(TEST_ACCOUNT_SPACE_ID))
                .verifyComplete();
    }

    @Test
    void getProviderForSpace_ShouldReturnProvider_WhenProviderExistsForAccountSpace() {
        // Arrange
        when(repository.findById(eq(TEST_PROVIDER_ID))).thenReturn(Mono.just(testAccountProvider));
        when(mapper.toDTO(any(AccountProvider.class))).thenReturn(testAccountProviderDTO);

        // Act
        Mono<AccountProviderDTO> result = accountProviderService.getProviderForSpace(
                TEST_ACCOUNT_ID, TEST_ACCOUNT_SPACE_ID, TEST_PROVIDER_ID);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto -> 
                        dto.getAccountProviderId().equals(TEST_PROVIDER_ID) &&
                        dto.getAccountId().equals(TEST_ACCOUNT_ID) &&
                        dto.getAccountSpaceId().equals(TEST_ACCOUNT_SPACE_ID))
                .verifyComplete();
    }

    @Test
    void getProviderForSpace_ShouldReturnEmptyMono_WhenProviderDoesNotExistForAccountSpace() {
        // Arrange
        when(repository.findById(eq(TEST_PROVIDER_ID))).thenReturn(Mono.empty());

        // Act
        Mono<AccountProviderDTO> result = accountProviderService.getProviderForSpace(
                TEST_ACCOUNT_ID, TEST_ACCOUNT_SPACE_ID, TEST_PROVIDER_ID);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void updateProviderForSpace_ShouldReturnUpdatedProvider_WhenProviderExistsForAccountSpace() {
        // Arrange
        when(repository.findById(eq(TEST_PROVIDER_ID))).thenReturn(Mono.just(testAccountProvider));
        when(mapper.toEntity(any(AccountProviderDTO.class))).thenReturn(testAccountProvider);
        when(repository.save(any(AccountProvider.class))).thenReturn(Mono.just(testAccountProvider));
        when(mapper.toDTO(any(AccountProvider.class))).thenReturn(testAccountProviderDTO);

        // Act
        Mono<AccountProviderDTO> result = accountProviderService.updateProviderForSpace(
                TEST_ACCOUNT_ID, TEST_ACCOUNT_SPACE_ID, TEST_PROVIDER_ID, testAccountProviderDTO);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto -> 
                        dto.getAccountProviderId().equals(TEST_PROVIDER_ID) &&
                        dto.getAccountId().equals(TEST_ACCOUNT_ID) &&
                        dto.getAccountSpaceId().equals(TEST_ACCOUNT_SPACE_ID))
                .verifyComplete();
    }

    @Test
    void deleteProviderForSpace_ShouldDeleteProvider_WhenProviderExistsForAccountSpace() {
        // Arrange
        when(repository.findById(eq(TEST_PROVIDER_ID))).thenReturn(Mono.just(testAccountProvider));
        when(repository.delete(any(AccountProvider.class))).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = accountProviderService.deleteProviderForSpace(
                TEST_ACCOUNT_ID, TEST_ACCOUNT_SPACE_ID, TEST_PROVIDER_ID);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
    }
}
