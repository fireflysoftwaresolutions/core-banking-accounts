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


package io.fireflysoftware.core.banking.accounts.core.services.mandate.impl;

import io.fireflysoftware.core.banking.accounts.core.operations.DepositAccountRules;
import io.fireflysoftware.core.banking.accounts.core.services.mandate.ServicingMandateService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.mandate.ServicingMandateDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.mandate.ServicingMandate;
import io.fireflysoftware.core.banking.accounts.models.repositories.mandate.ServicingMandateRepository;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.util.UUID;

@Service("servicingMandateService")
public class ServicingMandateServiceImpl implements ServicingMandateService {

    private final ServicingMandateRepository repository;

    public ServicingMandateServiceImpl(ServicingMandateRepository repository) {
        this.repository = repository;
    }

    public Mono<ServicingMandateDTO> create(UUID accountId, ServicingMandateDTO request) {
        if (accountId == null || request == null || request.partyId() == null || request.role() == null) {
            return Mono.error(new IllegalArgumentException("Account, party, and role are required"));
        }
        boolean active = request.active() == null || request.active();
        ServicingMandate row = new ServicingMandate();
        row.setServicingMandateId(UUID.randomUUID());
        row.setAccountId(accountId);
        row.setPartyId(request.partyId());
        row.setRole(request.role());
        row.setActive(active);
        return repository.save(row).map(this::toDto);
    }

    public Mono<ServicingMandateDTO> get(UUID accountId, UUID mandateId) {
        return repository.findById(mandateId)
                .filter(row -> accountId.equals(row.getAccountId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Servicing mandate was not found")))
                .map(this::toDto);
    }

    public boolean mayMoveMoney(ServicingMandateDTO mandate) {
        return mandate != null && DepositAccountRules.mayMoveMoney(
                DepositAccountRules.MandateRole.valueOf(mandate.role().name()),
                Boolean.TRUE.equals(mandate.active()));
    }

    private ServicingMandateDTO toDto(ServicingMandate row) {
        return new ServicingMandateDTO(row.getServicingMandateId(), row.getAccountId(), row.getPartyId(),
                row.getRole(), row.getActive());
    }
}
