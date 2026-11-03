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


package io.fireflysoftware.core.banking.accounts.core.services.parameter.impl;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import io.fireflysoftware.core.banking.accounts.core.mappers.parameter.AccountParameterMapper;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.parameter.AccountParameterDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.parameter.ParamTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.parameter.AccountParameter;
import io.fireflysoftware.core.banking.accounts.models.repositories.parameter.AccountParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AccountParameterServiceImplTest {

    @Mock
    private AccountParameterRepository repository;

    @Mock
    private AccountParameterMapper mapper;

    @InjectMocks
    private AccountParameterServiceImpl accountParameterService;

    private AccountParameter testAccountParameter;
    private AccountParameterDTO testAccountParameterDTO;
    private final UUID TEST_ACCOUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    private final UUID TEST_PARAMETER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");

    @BeforeEach
    void setUp() {
        // Setup test account parameter entity
        testAccountParameter = new AccountParameter();
        testAccountParameter.setAccountParameterId(TEST_PARAMETER_ID);
        testAccountParameter.setAccountId(TEST_ACCOUNT_ID);
        testAccountParameter.setParamType(ParamTypeEnum.MONTHLY_FEE);
        testAccountParameter.setParamValue(new BigDecimal("5.0000"));
        testAccountParameter.setParamUnit("EUR");
        testAccountParameter.setEffectiveDate(LocalDateTime.now());
        testAccountParameter.setExpiryDate(LocalDateTime.now().plusYears(1));
        testAccountParameter.setDescription("Test Monthly Fee");

        // Setup test account parameter DTO
        testAccountParameterDTO = AccountParameterDTO.builder()
                .accountParameterId(TEST_PARAMETER_ID)
                .accountId(TEST_ACCOUNT_ID)
                .paramType(ParamTypeEnum.MONTHLY_FEE)
                .paramValue(new BigDecimal("5.0000"))
                .paramUnit("EUR")
                .effectiveDate(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusYears(1))
                .description("Test Monthly Fee")
                .build();
    }

    @Test
    void listParameters_ShouldReturnPaginatedParameters() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest();
        
        // Create a mock PaginationResponse
        @SuppressWarnings("unchecked")
        PaginationResponse<AccountParameterDTO> mockResponse = mock(PaginationResponse.class);
        
        // Mock the static PaginationUtils.paginateQuery method
        try (MockedStatic<PaginationUtils> mockedPaginationUtils = mockStatic(PaginationUtils.class)) {
            mockedPaginationUtils.when(() -> PaginationUtils.paginateQuery(
                    any(PaginationRequest.class),
                    any(Function.class),
                    any(Function.class),
                    any(Supplier.class)
            )).thenReturn(Mono.just(mockResponse));
            
            // Act & Assert
            StepVerifier.create(accountParameterService.listParameters(TEST_ACCOUNT_ID, paginationRequest))
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
    void createParameter_ShouldReturnCreatedParameter() {
        // Arrange
        when(mapper.toEntity(any(AccountParameterDTO.class))).thenReturn(testAccountParameter);
        when(repository.save(any(AccountParameter.class))).thenReturn(Mono.just(testAccountParameter));
        when(mapper.toDTO(any(AccountParameter.class))).thenReturn(testAccountParameterDTO);

        // Act & Assert
        StepVerifier.create(accountParameterService.createParameter(TEST_ACCOUNT_ID, testAccountParameterDTO))
                .expectNext(testAccountParameterDTO)
                .verifyComplete();

        verify(mapper).toEntity(testAccountParameterDTO);
        verify(repository).save(testAccountParameter);
        verify(mapper).toDTO(testAccountParameter);
    }

    @Test
    void getParameter_ShouldReturnParameter_WhenParameterExistsForAccount() {
        // Arrange
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.just(testAccountParameter));
        when(mapper.toDTO(testAccountParameter)).thenReturn(testAccountParameterDTO);

        // Act & Assert
        StepVerifier.create(accountParameterService.getParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID))
                .expectNext(testAccountParameterDTO)
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verify(mapper).toDTO(testAccountParameter);
    }

    @Test
    void getParameter_ShouldReturnEmptyMono_WhenParameterDoesNotExistForAccount() {
        // Arrange
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountParameterService.getParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void getParameter_ShouldReturnEmptyMono_WhenParameterExistsButNotForSpecifiedAccount() {
        // Arrange
        AccountParameter parameterForDifferentAccount = new AccountParameter();
        parameterForDifferentAccount.setAccountParameterId(TEST_PARAMETER_ID);
        parameterForDifferentAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440055")); // Different account ID
        
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.just(parameterForDifferentAccount));

        // Act & Assert
        StepVerifier.create(accountParameterService.getParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void updateParameter_ShouldReturnUpdatedParameter_WhenParameterExistsForAccount() {
        // Arrange
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.just(testAccountParameter));
        when(mapper.toEntity(testAccountParameterDTO)).thenReturn(testAccountParameter);
        when(repository.save(testAccountParameter)).thenReturn(Mono.just(testAccountParameter));
        when(mapper.toDTO(testAccountParameter)).thenReturn(testAccountParameterDTO);

        // Act & Assert
        StepVerifier.create(accountParameterService.updateParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID, testAccountParameterDTO))
                .expectNext(testAccountParameterDTO)
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verify(mapper).toEntity(testAccountParameterDTO);
        verify(repository).save(testAccountParameter);
        verify(mapper).toDTO(testAccountParameter);
    }

    @Test
    void updateParameter_ShouldReturnEmptyMono_WhenParameterDoesNotExistForAccount() {
        // Arrange
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountParameterService.updateParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID, testAccountParameterDTO))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verifyNoMoreInteractions(mapper, repository);
    }

    @Test
    void updateParameter_ShouldReturnEmptyMono_WhenParameterExistsButNotForSpecifiedAccount() {
        // Arrange
        AccountParameter parameterForDifferentAccount = new AccountParameter();
        parameterForDifferentAccount.setAccountParameterId(TEST_PARAMETER_ID);
        parameterForDifferentAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440056")); // Different account ID
        
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.just(parameterForDifferentAccount));

        // Act & Assert
        StepVerifier.create(accountParameterService.updateParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID, testAccountParameterDTO))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verifyNoMoreInteractions(mapper, repository);
    }

    @Test
    void deleteParameter_ShouldDeleteParameter_WhenParameterExistsForAccount() {
        // Arrange
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.just(testAccountParameter));
        when(repository.delete(testAccountParameter)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountParameterService.deleteParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verify(repository).delete(testAccountParameter);
    }

    @Test
    void deleteParameter_ShouldReturnEmptyMono_WhenParameterDoesNotExistForAccount() {
        // Arrange
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(accountParameterService.deleteParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteParameter_ShouldReturnEmptyMono_WhenParameterExistsButNotForSpecifiedAccount() {
        // Arrange
        AccountParameter parameterForDifferentAccount = new AccountParameter();
        parameterForDifferentAccount.setAccountParameterId(TEST_PARAMETER_ID);
        parameterForDifferentAccount.setAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440057")); // Different account ID
        
        when(repository.findById(TEST_PARAMETER_ID)).thenReturn(Mono.just(parameterForDifferentAccount));

        // Act & Assert
        StepVerifier.create(accountParameterService.deleteParameter(TEST_ACCOUNT_ID, TEST_PARAMETER_ID))
                .verifyComplete();

        verify(repository).findById(TEST_PARAMETER_ID);
        verifyNoMoreInteractions(repository);
    }
}