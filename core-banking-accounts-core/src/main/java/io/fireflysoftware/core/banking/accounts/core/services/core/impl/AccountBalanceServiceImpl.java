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


package io.fireflysoftware.core.banking.accounts.core.services.core.impl;

import io.fireflysoftware.core.banking.accounts.core.mappers.core.AccountBalanceMapper;
import io.fireflysoftware.core.banking.accounts.core.services.core.AccountBalanceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountBalanceDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.core.AccountBalance;
import io.fireflysoftware.core.banking.accounts.models.repositories.core.AccountBalanceRepository;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Transactional
public class AccountBalanceServiceImpl implements AccountBalanceService {

    @Autowired
    private AccountBalanceRepository repository;

    @Autowired
    private AccountBalanceMapper mapper;

    @Override
    public Mono<PaginationResponse<AccountBalanceDTO>> getAllBalances(UUID accountId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountId(accountId, pageable),
                () -> repository.countByAccountId(accountId)
        );
    }

    @Override
    public Mono<PaginationResponse<AccountBalanceDTO>> getGlobalBalances(UUID accountId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountIdAndAccountSpaceIdIsNull(accountId, pageable),
                () -> repository.countByAccountIdAndAccountSpaceIdIsNull(accountId)
        );
    }

    @Override
    public Mono<PaginationResponse<AccountBalanceDTO>> getSpaceBalances(UUID accountId, UUID accountSpaceId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountIdAndAccountSpaceId(accountId, accountSpaceId, pageable),
                () -> repository.countByAccountIdAndAccountSpaceId(accountId, accountSpaceId)
        );
    }

    @Override
    public Mono<AccountBalanceDTO> createBalance(UUID accountId, AccountBalanceDTO balanceDTO) {
        balanceDTO.setAccountId(accountId);
        AccountBalance accountBalance = mapper.toEntity(balanceDTO);
        return repository.save(accountBalance)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountBalanceDTO> getBalance(UUID accountId, UUID balanceId) {
        return repository.findById(balanceId)
                .filter(balance -> balance.getAccountId().equals(accountId))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountBalanceDTO> updateBalance(UUID accountId, UUID balanceId, AccountBalanceDTO balanceDTO) {
        return repository.findById(balanceId)
                .filter(balance -> balance.getAccountId().equals(accountId))
                .flatMap(existingBalance -> {
                    balanceDTO.setAccountBalanceId(balanceId);
                    balanceDTO.setAccountId(accountId);
                    AccountBalance updatedBalance = mapper.toEntity(balanceDTO);
                    return repository.save(updatedBalance);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteBalance(UUID accountId, UUID balanceId) {
        return repository.findById(balanceId)
                .filter(balance -> balance.getAccountId().equals(accountId))
                .flatMap(repository::delete);
    }
}
