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


package io.fireflysoftware.core.banking.accounts.web.controllers.core;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.services.core.AccountService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Tag(name = "Accounts", description = "APIs for managing bank accounts within the system")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @Autowired
    private AccountService service;

    @Operation(
            summary = "Filter Accounts",
            description = "Retrieve a paginated list of all bank accounts based on filter criteria."
    )
    @PostMapping(value = "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AccountDTO>>> filterAccounts(
            @RequestBody FilterRequest<AccountDTO> filterRequest
    ) {
        return service.filterAccounts(filterRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }


    @Operation(
            summary = "Create Account",
            description = "Create a new bank account with the specified details."
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountDTO>> createAccount(
            @Parameter(description = "Data for the new account", required = true,
                    schema = @Schema(implementation = AccountDTO.class))
            @RequestBody AccountDTO accountDTO
    ) {
        return service.createAccount(accountDTO)
                .map(createdAccount -> ResponseEntity.status(201).body(createdAccount))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Get Account by ID",
            description = "Retrieve an existing bank account by its unique identifier."
    )
    @GetMapping(value = "/{accountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountDTO>> getAccount(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return service.getAccount(accountId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Update Account",
            description = "Update the details of an existing bank account by its unique identifier."
    )
    @PutMapping(value = "/{accountId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountDTO>> updateAccount(
            @Parameter(description = "Unique identifier of the account to update", required = true)
            @PathVariable("accountId") UUID accountId,

            @Parameter(description = "Updated account data", required = true,
                    schema = @Schema(implementation = AccountDTO.class))
            @RequestBody AccountDTO accountDTO
    ) {
        return service.updateAccount(accountId, accountDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Account",
            description = "Delete an existing bank account by its unique identifier."
    )
    @DeleteMapping(value = "/{accountId}")
    public Mono<ResponseEntity<Void>> deleteAccount(
            @Parameter(description = "Unique identifier of the account to delete", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return service.deleteAccount(accountId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}