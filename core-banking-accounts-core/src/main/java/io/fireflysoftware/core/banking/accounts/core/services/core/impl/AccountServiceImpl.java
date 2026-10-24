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

import io.fireflysoftware.core.banking.accounts.core.mappers.core.AccountMapper;
import io.fireflysoftware.core.banking.accounts.core.services.core.AccountService;
import io.fireflysoftware.core.banking.accounts.core.services.space.AccountSpaceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.AccountSpaceDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountStatusEnum;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.space.AccountSpaceTypeEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.core.Account;
import io.fireflysoftware.core.banking.accounts.models.repositories.core.AccountRepository;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountRepository repository;

    @Autowired
    private AccountMapper mapper;

    @Autowired
    private AccountSpaceService accountSpaceService;

    @Override
    public Mono<PaginationResponse<AccountDTO>> filterAccounts(FilterRequest<AccountDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Account.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<AccountDTO> createAccount(AccountDTO accountDTO) {
        Account account = mapper.toEntity(accountDTO);
        if (account.getAccountStatus() == null) {
            account.setAccountStatus(AccountStatusEnum.DRAFT);
        }
        return repository.save(account)
                .flatMap(savedAccount -> {
                    // Create a default MAIN space for the account
                    AccountSpaceDTO mainSpace = new AccountSpaceDTO();
                    mainSpace.setAccountId(savedAccount.getAccountId());
                    mainSpace.setSpaceName("Main Account");
                    mainSpace.setSpaceType(AccountSpaceTypeEnum.MAIN);
                    mainSpace.setBalance(BigDecimal.ZERO); // Initial balance
                    mainSpace.setIsVisible(true);
                    mainSpace.setDescription("Primary account space");

                    return accountSpaceService.createAccountSpace(mainSpace)
                            .thenReturn(savedAccount);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountDTO> getAccount(UUID accountId) {
        return repository.findById(accountId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountDTO> updateAccount(UUID accountId, AccountDTO accountDTO) {
        return repository.findById(accountId)
                .flatMap(existingAccount -> {
                    AccountStatusEnum current = existingAccount.getAccountStatus() != null
                            ? existingAccount.getAccountStatus()
                            : AccountStatusEnum.DRAFT;
                    mapper.updateEntityFromDto(accountDTO, existingAccount);
                    existingAccount.setAccountId(accountId);
                    AccountStatusEnum next = existingAccount.getAccountStatus() != null
                            ? existingAccount.getAccountStatus()
                            : current;
                    if (!AccountLifecycle.canTransition(current, next)) {
                        return Mono.error(new IllegalStateException(
                                "Account " + accountId + " cannot move from " + current + " to " + next));
                    }
                    existingAccount.setAccountStatus(next);
                    return repository.save(existingAccount);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteAccount(UUID accountId) {
        return repository.findById(accountId)
                .flatMap(existingAccount -> {
                    AccountStatusEnum status = existingAccount.getAccountStatus() != null
                            ? existingAccount.getAccountStatus()
                            : AccountStatusEnum.DRAFT;
                    if (status != AccountStatusEnum.DRAFT) {
                        return Mono.error(new IllegalStateException(
                                "Account " + accountId + " is " + status
                                        + " and cannot be deleted; close it so the account history is kept"));
                    }
                    return repository.delete(existingAccount);
                });
    }
}
