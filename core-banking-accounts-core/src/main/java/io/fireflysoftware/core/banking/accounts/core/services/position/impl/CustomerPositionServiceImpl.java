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


package io.fireflysoftware.core.banking.accounts.core.services.position.impl;

import io.fireflysoftware.core.banking.accounts.core.operations.DepositAccountRules;
import io.fireflysoftware.core.banking.accounts.core.services.position.CustomerPositionService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.position.CustomerPositionDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.position.CustomerPosition;
import io.fireflysoftware.core.banking.accounts.models.repositories.position.CustomerPositionRepository;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.util.UUID;

@Service("customerPositionService")
public class CustomerPositionServiceImpl implements CustomerPositionService {

    private final CustomerPositionRepository repository;

    public CustomerPositionServiceImpl(CustomerPositionRepository repository) {
        this.repository = repository;
    }

    public Mono<CustomerPositionDTO> record(CustomerPositionDTO request) {
        if (request == null || request.partyId() == null) {
            return Mono.error(new IllegalArgumentException("Party is required"));
        }
        if (request.currency() == null || !request.currency().matches("[A-Z]{3}")) {
            return Mono.error(new IllegalArgumentException("Currency must be a 3-letter code"));
        }
        DepositAccountRules.Position calculated = DepositAccountRules.customerPosition(
                request.balances(), request.creditLimit(), request.creditUsed(),
                request.collateralValue(), request.taxWithheld());
        CustomerPosition row = new CustomerPosition();
        row.setCustomerPositionId(UUID.randomUUID());
        row.setPartyId(request.partyId());
        row.setCurrency(request.currency());
        row.setBalances(request.balances());
        row.setCreditLimit(request.creditLimit());
        row.setCreditUsed(request.creditUsed());
        row.setCollateralValue(calculated.collateralValue());
        row.setTaxWithheld(calculated.taxWithheld());
        row.setAvailableFunds(calculated.availableFunds());
        row.setCreditRemaining(calculated.creditRemaining());
        return repository.save(row).map(this::toDto);
    }

    public Mono<CustomerPositionDTO> get(UUID positionId) {
        return repository.findById(positionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Customer position was not found")))
                .map(this::toDto);
    }

    private CustomerPositionDTO toDto(CustomerPosition row) {
        return new CustomerPositionDTO(row.getCustomerPositionId(), row.getPartyId(), row.getCurrency(),
                row.getBalances(), row.getCreditLimit(), row.getCreditUsed(), row.getCollateralValue(),
                row.getTaxWithheld(), row.getAvailableFunds(), row.getCreditRemaining());
    }
}
