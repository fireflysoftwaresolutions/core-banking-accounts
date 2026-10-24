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


package io.fireflysoftware.core.banking.accounts.core.services.restriction.impl;

import io.fireflysoftware.core.banking.accounts.core.mappers.restriction.AccountRestrictionMapper;
import io.fireflysoftware.core.banking.accounts.core.services.restriction.AccountRestrictionService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.restriction.AccountRestrictionDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.restriction.AccountRestriction;
import io.fireflysoftware.core.banking.accounts.models.repositories.restriction.AccountRestrictionRepository;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AccountRestrictionServiceImpl implements AccountRestrictionService {

    private static final Logger logger = LoggerFactory.getLogger(AccountRestrictionServiceImpl.class);

    private static final String ERROR_RESTRICTION_ID_REQUIRED = "Account restriction ID is required";
    private static final String ERROR_RESTRICTION_NOT_FOUND = "Account restriction not found with ID: %s";
    private static final String ERROR_ACCOUNT_ID_REQUIRED = "Account ID is required";
    private static final String ERROR_RESTRICTION_TYPE_REQUIRED = "Restriction type is required";
    private static final String ERROR_START_DATE_REQUIRED = "Start date is required";
    private static final String ERROR_REFERENCE_NUMBER_REQUIRED = "Reference number is required";
    private static final String ERROR_REASON_REQUIRED = "Reason is required";
    private static final String ERROR_APPLIED_BY_REQUIRED = "Applied by is required";
    private static final String ERROR_REMOVED_BY_REQUIRED = "Removed by is required";
    private static final String ERROR_RESTRICTION_ALREADY_REMOVED = "Restriction is already removed";

    @Autowired
    private AccountRestrictionRepository repository;

    @Autowired
    private AccountRestrictionMapper mapper;

    @Override
    public Mono<AccountRestrictionDTO> createAccountRestriction(AccountRestrictionDTO accountRestrictionDTO) {
        // Validate required fields
        if (accountRestrictionDTO.getAccountId() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }
        if (accountRestrictionDTO.getRestrictionType() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_RESTRICTION_TYPE_REQUIRED));
        }
        if (accountRestrictionDTO.getStartDateTime() == null) {
            return Mono.error(new IllegalArgumentException(ERROR_START_DATE_REQUIRED));
        }
        if (accountRestrictionDTO.getReferenceNumber() == null || accountRestrictionDTO.getReferenceNumber().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_REFERENCE_NUMBER_REQUIRED));
        }
        if (accountRestrictionDTO.getReason() == null || accountRestrictionDTO.getReason().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_REASON_REQUIRED));
        }
        if (accountRestrictionDTO.getAppliedBy() == null || accountRestrictionDTO.getAppliedBy().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_APPLIED_BY_REQUIRED));
        }

        // Set default values if not provided
        if (accountRestrictionDTO.getIsActive() == null) {
            accountRestrictionDTO.setIsActive(true);
        }

        AccountRestriction accountRestriction = mapper.toEntity(accountRestrictionDTO);
        return repository.save(accountRestriction)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountRestrictionDTO> getAccountRestriction(UUID accountRestrictionId) {
        if (accountRestrictionId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_RESTRICTION_ID_REQUIRED));
        }

        return repository.findById(accountRestrictionId)
                .map(mapper::toDTO)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_RESTRICTION_NOT_FOUND, accountRestrictionId))));
    }

    @Override
    public Mono<AccountRestrictionDTO> updateAccountRestriction(UUID accountRestrictionId, AccountRestrictionDTO accountRestrictionDTO) {
        if (accountRestrictionId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_RESTRICTION_ID_REQUIRED));
        }

        return repository.findById(accountRestrictionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_RESTRICTION_NOT_FOUND, accountRestrictionId))))
                .flatMap(existingRestriction -> {
                    // Update fields
                    if (accountRestrictionDTO.getRestrictionType() != null) {
                        existingRestriction.setRestrictionType(accountRestrictionDTO.getRestrictionType());
                    }
                    if (accountRestrictionDTO.getStartDateTime() != null) {
                        existingRestriction.setStartDateTime(accountRestrictionDTO.getStartDateTime());
                    }
                    if (accountRestrictionDTO.getEndDateTime() != null) {
                        existingRestriction.setEndDateTime(accountRestrictionDTO.getEndDateTime());
                    }
                    if (accountRestrictionDTO.getRestrictedAmount() != null) {
                        existingRestriction.setRestrictedAmount(accountRestrictionDTO.getRestrictedAmount());
                    }
                    if (accountRestrictionDTO.getReferenceNumber() != null) {
                        existingRestriction.setReferenceNumber(accountRestrictionDTO.getReferenceNumber());
                    }
                    if (accountRestrictionDTO.getReason() != null) {
                        existingRestriction.setReason(accountRestrictionDTO.getReason());
                    }
                    if (accountRestrictionDTO.getNotes() != null) {
                        existingRestriction.setNotes(accountRestrictionDTO.getNotes());
                    }
                    if (accountRestrictionDTO.getIsActive() != null) {
                        existingRestriction.setIsActive(accountRestrictionDTO.getIsActive());
                    }
                    if (accountRestrictionDTO.getRemovedBy() != null) {
                        existingRestriction.setRemovedBy(accountRestrictionDTO.getRemovedBy());
                    }

                    return repository.save(existingRestriction);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteAccountRestriction(UUID accountRestrictionId) {
        if (accountRestrictionId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_RESTRICTION_ID_REQUIRED));
        }

        return repository.findById(accountRestrictionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_RESTRICTION_NOT_FOUND, accountRestrictionId))))
                .flatMap(restriction -> repository.delete(restriction));
    }

    @Override
    public Flux<AccountRestrictionDTO> getAccountRestrictionsByAccountId(UUID accountId) {
        if (accountId == null) {
            return Flux.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        return repository.findByAccountId(accountId)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<AccountRestrictionDTO> getActiveAccountRestrictionsByAccountId(UUID accountId) {
        if (accountId == null) {
            return Flux.error(new IllegalArgumentException(ERROR_ACCOUNT_ID_REQUIRED));
        }

        return repository.findByAccountIdAndIsActive(accountId, true)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<AccountRestrictionDTO> removeRestriction(UUID accountRestrictionId, String removedBy) {
        if (accountRestrictionId == null) {
            return Mono.error(new IllegalArgumentException(ERROR_RESTRICTION_ID_REQUIRED));
        }
        if (removedBy == null || removedBy.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException(ERROR_REMOVED_BY_REQUIRED));
        }

        return repository.findById(accountRestrictionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format(ERROR_RESTRICTION_NOT_FOUND, accountRestrictionId))))
                .flatMap(restriction -> {
                    if (!restriction.getIsActive()) {
                        return Mono.error(new IllegalStateException(ERROR_RESTRICTION_ALREADY_REMOVED));
                    }

                    restriction.setIsActive(false);
                    restriction.setRemovedBy(removedBy);
                    restriction.setEndDateTime(LocalDateTime.now());

                    return repository.save(restriction);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<AccountRestrictionDTO>> listAccountRestrictions(FilterRequest filterRequest) {
        return FilterUtils
                .createFilter(
                        AccountRestriction.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }
}
