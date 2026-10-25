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

import io.fireflysoftware.core.banking.accounts.core.mappers.provider.AccountProviderMapper;
import io.fireflysoftware.core.banking.accounts.core.services.provider.AccountProviderService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.provider.AccountProviderDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.provider.AccountProvider;
import io.fireflysoftware.core.banking.accounts.models.repositories.provider.AccountProviderRepository;

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
public class AccountProviderServiceImpl implements AccountProviderService {

    @Autowired
    private AccountProviderRepository repository;

    @Autowired
    private AccountProviderMapper mapper;

    @Override
    public Mono<PaginationResponse<AccountProviderDTO>> listProviders(UUID accountId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountId(accountId, pageable),
                () -> repository.countByAccountId(accountId)
        );
    }

    @Override
    public Mono<AccountProviderDTO> createProvider(UUID accountId, AccountProviderDTO providerDTO) {
        AccountProvider accountProvider = mapper.toEntity(providerDTO);
        accountProvider.setAccountId(accountId);
        return repository.save(accountProvider)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountProviderDTO> getProvider(UUID accountId, UUID providerId) {
        return repository.findById(providerId)
                .filter(provider -> provider.getAccountId().equals(accountId))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountProviderDTO> updateProvider(UUID accountId, UUID providerId, AccountProviderDTO providerDTO) {
        return repository.findById(providerId)
                .filter(provider -> provider.getAccountId().equals(accountId))
                .flatMap(existingProvider -> {
                    AccountProvider updatedProvider = mapper.toEntity(providerDTO);
                    updatedProvider.setAccountProviderId(existingProvider.getAccountProviderId());
                    updatedProvider.setAccountId(existingProvider.getAccountId());
                    return repository.save(updatedProvider);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteProvider(UUID accountId, UUID providerId) {
        return repository.findById(providerId)
                .filter(provider -> provider.getAccountId().equals(accountId))
                .flatMap(repository::delete);
    }

    @Override
    public Mono<PaginationResponse<AccountProviderDTO>> listProvidersForSpace(UUID accountId, UUID accountSpaceId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountIdAndAccountSpaceId(accountId, accountSpaceId, pageable),
                () -> repository.countByAccountIdAndAccountSpaceId(accountId, accountSpaceId)
        );
    }

    @Override
    public Mono<AccountProviderDTO> createProviderForSpace(UUID accountId, UUID accountSpaceId, AccountProviderDTO providerDTO) {
        AccountProvider accountProvider = mapper.toEntity(providerDTO);
        accountProvider.setAccountId(accountId);
        accountProvider.setAccountSpaceId(accountSpaceId);
        return repository.save(accountProvider)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountProviderDTO> getProviderForSpace(UUID accountId, UUID accountSpaceId, UUID providerId) {
        return repository.findById(providerId)
                .filter(provider -> provider.getAccountId().equals(accountId) && 
                        (provider.getAccountSpaceId() != null && provider.getAccountSpaceId().equals(accountSpaceId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountProviderDTO> updateProviderForSpace(UUID accountId, UUID accountSpaceId, UUID providerId, AccountProviderDTO providerDTO) {
        return repository.findById(providerId)
                .filter(provider -> provider.getAccountId().equals(accountId) && 
                        (provider.getAccountSpaceId() != null && provider.getAccountSpaceId().equals(accountSpaceId)))
                .flatMap(existingProvider -> {
                    AccountProvider updatedProvider = mapper.toEntity(providerDTO);
                    updatedProvider.setAccountProviderId(existingProvider.getAccountProviderId());
                    updatedProvider.setAccountId(existingProvider.getAccountId());
                    updatedProvider.setAccountSpaceId(existingProvider.getAccountSpaceId());
                    return repository.save(updatedProvider);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteProviderForSpace(UUID accountId, UUID accountSpaceId, UUID providerId) {
        return repository.findById(providerId)
                .filter(provider -> provider.getAccountId().equals(accountId) && 
                        (provider.getAccountSpaceId() != null && provider.getAccountSpaceId().equals(accountSpaceId)))
                .flatMap(repository::delete);
    }
}
