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

import io.fireflysoftware.core.banking.accounts.core.mappers.space.SpaceTransactionMapper;
import io.fireflysoftware.core.banking.accounts.core.services.space.AccountSpaceService;
import io.fireflysoftware.core.banking.accounts.core.services.space.AccountSpaceTransactionService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.SpaceTransactionDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.space.SpaceTransaction;
import io.fireflysoftware.core.banking.accounts.models.repositories.space.SpaceTransactionRepository;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of the AccountSpaceTransactionService interface.
 */
@Service
@Transactional
public class AccountSpaceTransactionServiceImpl implements AccountSpaceTransactionService {
    
    private static final Logger logger = LoggerFactory.getLogger(AccountSpaceTransactionServiceImpl.class);
    
    @Autowired
    private SpaceTransactionRepository repository;
    
    @Autowired
    private SpaceTransactionMapper mapper;
    
    @Autowired
    private AccountSpaceService accountSpaceService;
    
    @Override
    public Mono<SpaceTransactionDTO> recordTransaction(UUID accountSpaceId, BigDecimal amount, String description, String referenceId) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required"));
        }
        if (amount == null) {
            return Mono.error(new IllegalArgumentException("Transaction amount is required"));
        }
        
        // Determine transaction type
        String transactionType = amount.compareTo(BigDecimal.ZERO) > 0 ? "DEPOSIT" : "WITHDRAWAL";
        
        // Get the account space to update its balance and record the transaction
        return accountSpaceService.getAccountSpace(accountSpaceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Account space not found with ID: " + accountSpaceId)))
                .flatMap(spaceDTO -> {
                    // Calculate new balance
                    BigDecimal currentBalance = spaceDTO.getBalance();
                    BigDecimal newBalance = currentBalance.add(amount);
                    
                    // Create transaction record
                    SpaceTransactionDTO transactionDTO = new SpaceTransactionDTO();
                    transactionDTO.setAccountSpaceId(accountSpaceId);
                    transactionDTO.setAmount(amount);
                    transactionDTO.setBalanceAfterTransaction(newBalance);
                    transactionDTO.setTransactionDateTime(LocalDateTime.now());
                    transactionDTO.setDescription(description);
                    transactionDTO.setReferenceId(referenceId);
                    transactionDTO.setTransactionType(transactionType);
                    transactionDTO.setSpaceName(spaceDTO.getSpaceName());
                    transactionDTO.setAccountId(spaceDTO.getAccountId());
                    
                    SpaceTransaction transaction = mapper.toEntity(transactionDTO);
                    
                    // Update account space balance
                    spaceDTO.setBalance(newBalance);
                    
                    // Save transaction and update space balance
                    return repository.save(transaction)
                            .flatMap(savedTransaction -> 
                                accountSpaceService.updateAccountSpace(accountSpaceId, spaceDTO)
                                    .thenReturn(savedTransaction)
                            )
                            .map(mapper::toDTO);
                });
    }
    
    @Override
    public Mono<PaginationResponse<SpaceTransactionDTO>> getTransactions(UUID accountSpaceId, PaginationRequest paginationRequest) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required"));
        }
        
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountSpaceId(accountSpaceId, pageable),
                () -> repository.countByAccountSpaceId(accountSpaceId)
        );
    }
    
    @Override
    public Mono<PaginationResponse<SpaceTransactionDTO>> getTransactionsByDateRange(
            UUID accountSpaceId, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            PaginationRequest paginationRequest) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required"));
        }
        if (startDate == null) {
            return Mono.error(new IllegalArgumentException("Start date is required"));
        }
        if (endDate == null) {
            return Mono.error(new IllegalArgumentException("End date is required"));
        }
        if (startDate.isAfter(endDate)) {
            return Mono.error(new IllegalArgumentException("Start date must be before or equal to end date"));
        }
        
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountSpaceIdAndTransactionDateTimeBetween(
                        accountSpaceId, startDate, endDate, pageable),
                () -> repository.countByAccountSpaceIdAndTransactionDateTimeBetween(
                        accountSpaceId, startDate, endDate)
        );
    }
    
    @Override
    public Mono<BigDecimal> calculateTotalDeposits(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required"));
        }
        if (startDate == null) {
            return Mono.error(new IllegalArgumentException("Start date is required"));
        }
        if (endDate == null) {
            return Mono.error(new IllegalArgumentException("End date is required"));
        }
        if (startDate.isAfter(endDate)) {
            return Mono.error(new IllegalArgumentException("Start date must be before or equal to end date"));
        }
        
        return repository.calculateTotalDeposits(accountSpaceId, startDate, endDate);
    }
    
    @Override
    public Mono<BigDecimal> calculateTotalWithdrawals(UUID accountSpaceId, LocalDateTime startDate, LocalDateTime endDate) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required"));
        }
        if (startDate == null) {
            return Mono.error(new IllegalArgumentException("Start date is required"));
        }
        if (endDate == null) {
            return Mono.error(new IllegalArgumentException("End date is required"));
        }
        if (startDate.isAfter(endDate)) {
            return Mono.error(new IllegalArgumentException("Start date must be before or equal to end date"));
        }
        
        return repository.calculateTotalWithdrawals(accountSpaceId, startDate, endDate);
    }
    
    @Override
    public Mono<BigDecimal> getBalanceAtDateTime(UUID accountSpaceId, LocalDateTime dateTime) {
        if (accountSpaceId == null) {
            return Mono.error(new IllegalArgumentException("Account space ID is required"));
        }
        if (dateTime == null) {
            return Mono.error(new IllegalArgumentException("Date time is required"));
        }
        
        return repository.findFirstByAccountSpaceIdAndTransactionDateTimeLessThanEqualOrderByTransactionDateTimeDesc(
                accountSpaceId, dateTime)
                .map(SpaceTransaction::getBalanceAfterTransaction)
                .switchIfEmpty(Mono.just(BigDecimal.ZERO));
    }
}
