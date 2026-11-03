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


package io.fireflysoftware.core.banking.accounts.core.services.space.impl;

import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.mappers.space.AccountSpaceMapper;
import io.fireflysoftware.core.banking.accounts.core.services.core.AccountBalanceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountBalanceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.AccountSpaceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.space.AccountSpace;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.AccountSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AccountSpaceServiceImplTest {

    @Mock
    private AccountSpaceRepository repository;

    @Mock
    private AccountSpaceMapper mapper;

    @Mock
    private AccountBalanceService accountBalanceService;

    @InjectMocks
    private AccountSpaceServiceImpl service;

    private static final UUID ACCOUNT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
    private static final UUID SPACE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");

    private AccountSpace accountSpace;
    private AccountSpaceDTO accountSpaceDTO;

    @BeforeEach
    void setUp() {
        // Setup test data
        accountSpace = new AccountSpace();
        accountSpace.setAccountSpaceId(SPACE_ID);
        accountSpace.setAccountId(ACCOUNT_ID);
        accountSpace.setSpaceName("Test Space");
        accountSpace.setSpaceType(AccountSpaceTypeEnum.SAVINGS);
        accountSpace.setBalance(BigDecimal.valueOf(1000));
        accountSpace.setIsFrozen(false);

        accountSpaceDTO = new AccountSpaceDTO();
        accountSpaceDTO.setAccountSpaceId(SPACE_ID);
        accountSpaceDTO.setAccountId(ACCOUNT_ID);
        accountSpaceDTO.setSpaceName("Test Space");
        accountSpaceDTO.setSpaceType(AccountSpaceTypeEnum.SAVINGS);
        accountSpaceDTO.setBalance(BigDecimal.valueOf(1000));
        accountSpaceDTO.setIsFrozen(false);
    }

    @Test
    void freezeAccountSpace_Success() {
        // Arrange
        AccountSpace frozenSpace = new AccountSpace();
        frozenSpace.setAccountSpaceId(SPACE_ID);
        frozenSpace.setAccountId(ACCOUNT_ID);
        frozenSpace.setIsFrozen(true);
        frozenSpace.setFrozenDateTime(LocalDateTime.now());

        AccountSpaceDTO frozenSpaceDTO = new AccountSpaceDTO();
        frozenSpaceDTO.setAccountSpaceId(SPACE_ID);
        frozenSpaceDTO.setAccountId(ACCOUNT_ID);
        frozenSpaceDTO.setIsFrozen(true);
        frozenSpaceDTO.setFrozenDateTime(frozenSpace.getFrozenDateTime());

        when(repository.findById(SPACE_ID)).thenReturn(Mono.just(accountSpace));
        when(repository.save(any(AccountSpace.class))).thenReturn(Mono.just(frozenSpace));
        when(mapper.toDTO(frozenSpace)).thenReturn(frozenSpaceDTO);

        // Act & Assert
        StepVerifier.create(service.freezeAccountSpace(SPACE_ID))
                .expectNext(frozenSpaceDTO)
                .verifyComplete();
    }

    @Test
    void freezeAccountSpace_AlreadyFrozen() {
        // Arrange
        accountSpace.setIsFrozen(true);
        when(repository.findById(SPACE_ID)).thenReturn(Mono.just(accountSpace));

        // Act & Assert
        StepVerifier.create(service.freezeAccountSpace(SPACE_ID))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void unfreezeAccountSpace_Success() {
        // Arrange
        accountSpace.setIsFrozen(true);

        AccountSpace unfrozenSpace = new AccountSpace();
        unfrozenSpace.setAccountSpaceId(SPACE_ID);
        unfrozenSpace.setAccountId(ACCOUNT_ID);
        unfrozenSpace.setIsFrozen(false);
        unfrozenSpace.setUnfrozenDateTime(LocalDateTime.now());

        AccountSpaceDTO unfrozenSpaceDTO = new AccountSpaceDTO();
        unfrozenSpaceDTO.setAccountSpaceId(SPACE_ID);
        unfrozenSpaceDTO.setAccountId(ACCOUNT_ID);
        unfrozenSpaceDTO.setIsFrozen(false);
        unfrozenSpaceDTO.setUnfrozenDateTime(unfrozenSpace.getUnfrozenDateTime());

        when(repository.findById(SPACE_ID)).thenReturn(Mono.just(accountSpace));
        when(repository.save(any(AccountSpace.class))).thenReturn(Mono.just(unfrozenSpace));
        when(mapper.toDTO(unfrozenSpace)).thenReturn(unfrozenSpaceDTO);

        // Act & Assert
        StepVerifier.create(service.unfreezeAccountSpace(SPACE_ID))
                .expectNext(unfrozenSpaceDTO)
                .verifyComplete();
    }

    @Test
    void unfreezeAccountSpace_NotFrozen() {
        // Arrange
        when(repository.findById(SPACE_ID)).thenReturn(Mono.just(accountSpace));

        // Act & Assert
        StepVerifier.create(service.unfreezeAccountSpace(SPACE_ID))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void updateAccountSpaceBalance_Success() {
        BigDecimal newBalance = BigDecimal.valueOf(2000);
        String reason = "Administrative adjustment";
        AccountSpace updated = new AccountSpace();
        updated.setAccountSpaceId(SPACE_ID);
        updated.setAccountId(ACCOUNT_ID);
        updated.setBalance(newBalance);

        when(repository.findById(SPACE_ID)).thenReturn(Mono.just(accountSpace));
        when(repository.save(any(AccountSpace.class))).thenReturn(Mono.just(updated));
        when(accountBalanceService.createBalance(eq(ACCOUNT_ID), any(AccountBalanceDTO.class)))
                .thenReturn(Mono.just(mock(AccountBalanceDTO.class)));
        when(mapper.toDTO(updated)).thenReturn(accountSpaceDTO);

        StepVerifier.create(service.updateAccountSpaceBalance(SPACE_ID, newBalance, reason))
                .expectNext(accountSpaceDTO)
                .verifyComplete();
    }

    @Test
    void updateAccountSpaceBalance_NegativeBalance() {
        // Arrange
        BigDecimal negativeBalance = BigDecimal.valueOf(-100);
        String reason = "Administrative adjustment";

        // Act & Assert
        StepVerifier.create(service.updateAccountSpaceBalance(SPACE_ID, negativeBalance, reason))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void updateAccountSpaceBalance_EmptyReason() {
        // Arrange
        BigDecimal newBalance = BigDecimal.valueOf(2000);
        String emptyReason = "";

        // Act & Assert
        StepVerifier.create(service.updateAccountSpaceBalance(SPACE_ID, newBalance, emptyReason))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void getSpaceAnalytics_Success() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();

        // Create balance history
        AccountBalanceDTO balance1 = AccountBalanceDTO.builder()
                .accountId(ACCOUNT_ID)
                .accountSpaceId(SPACE_ID)
                .balanceAmount(BigDecimal.valueOf(800))
                .asOfDatetime(startDate.plusDays(1))
                .build();

        AccountBalanceDTO balance2 = AccountBalanceDTO.builder()
                .accountId(ACCOUNT_ID)
                .accountSpaceId(SPACE_ID)
                .balanceAmount(BigDecimal.valueOf(900))
                .asOfDatetime(startDate.plusDays(15))
                .build();

        AccountBalanceDTO balance3 = AccountBalanceDTO.builder()
                .accountId(ACCOUNT_ID)
                .accountSpaceId(SPACE_ID)
                .balanceAmount(BigDecimal.valueOf(1000))
                .asOfDatetime(endDate.minusDays(1))
                .build();

        PaginationResponse<AccountBalanceDTO> balanceResponse = mock(PaginationResponse.class);
        when(balanceResponse.getContent()).thenReturn(List.of(balance1, balance2, balance3));

        when(repository.findById(SPACE_ID)).thenReturn(Mono.just(accountSpace));
        when(accountBalanceService.getSpaceBalances(eq(ACCOUNT_ID), eq(SPACE_ID), any()))
                .thenReturn(Mono.just(balanceResponse));

        // Act & Assert
        StepVerifier.create(service.getSpaceAnalytics(SPACE_ID, startDate, endDate))
                .expectNextMatches(analytics ->
                        analytics.getAccountSpaceId().equals(SPACE_ID) &&
                        analytics.getStartDate().equals(startDate) &&
                        analytics.getEndDate().equals(endDate) &&
                        analytics.getOpeningBalance().equals(BigDecimal.valueOf(800)) &&
                        analytics.getClosingBalance().equals(BigDecimal.valueOf(1000)) &&
                        analytics.getLowestBalance().equals(BigDecimal.valueOf(800)) &&
                        analytics.getHighestBalance().equals(BigDecimal.valueOf(1000)) &&
                        analytics.getBalanceHistory().size() == 3
                )
                .verifyComplete();
    }

    @Test
    void getSpaceAnalytics_InvalidDateRange() {
        // Arrange
        LocalDateTime startDate = LocalDateTime.now();
        LocalDateTime endDate = LocalDateTime.now().minusDays(1); // End before start

        // Act & Assert
        StepVerifier.create(service.getSpaceAnalytics(SPACE_ID, startDate, endDate))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
