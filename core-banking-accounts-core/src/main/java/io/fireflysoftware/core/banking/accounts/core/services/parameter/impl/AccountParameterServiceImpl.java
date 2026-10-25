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

import io.fireflysoftware.core.banking.accounts.core.mappers.parameter.AccountParameterMapper;
import io.fireflysoftware.core.banking.accounts.core.services.parameter.AccountParameterService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.parameter.AccountParameterDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.parameter.AccountParameter;
import io.fireflysoftware.core.banking.accounts.models.repositories.parameter.AccountParameterRepository;

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
public class AccountParameterServiceImpl implements AccountParameterService {

    @Autowired
    private AccountParameterRepository repository;

    @Autowired
    private AccountParameterMapper mapper;

    @Override
    public Mono<PaginationResponse<AccountParameterDTO>> listParameters(UUID accountId, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAccountId(accountId, pageable),
                () -> repository.countByAccountId(accountId)
        );
    }

    @Override
    public Mono<AccountParameterDTO> createParameter(UUID accountId, AccountParameterDTO parameterDTO) {
        parameterDTO.setAccountId(accountId);
        AccountParameter accountParameter = mapper.toEntity(parameterDTO);
        return repository.save(accountParameter)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountParameterDTO> getParameter(UUID accountId, UUID paramId) {
        return repository.findById(paramId)
                .filter(param -> param.getAccountId().equals(accountId))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountParameterDTO> updateParameter(UUID accountId, UUID paramId, AccountParameterDTO parameterDTO) {
        return repository.findById(paramId)
                .filter(param -> param.getAccountId().equals(accountId))
                .flatMap(existingParam -> {
                    parameterDTO.setAccountId(accountId);
                    parameterDTO.setAccountParameterId(paramId);
                    AccountParameter updatedParam = mapper.toEntity(parameterDTO);
                    return repository.save(updatedParam);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteParameter(UUID accountId, UUID paramId) {
        return repository.findById(paramId)
                .filter(param -> param.getAccountId().equals(accountId))
                .flatMap(repository::delete);
    }
}
