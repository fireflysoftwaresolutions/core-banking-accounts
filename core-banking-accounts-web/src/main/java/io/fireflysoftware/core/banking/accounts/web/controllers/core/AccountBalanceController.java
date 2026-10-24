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

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.services.core.AccountBalanceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.core.AccountBalanceDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Tag(name = "Account Balances", description = "APIs for managing balances associated with a specific bank account")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/balances")
public class AccountBalanceController {

    @Autowired
    private AccountBalanceService service;

    @Operation(
            summary = "List Account Balances",
            description = "Retrieve a paginated list of all balances linked to a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the balances",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No balances found for the specified account",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AccountBalanceDTO>>> getAllBalances(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.getAllBalances(accountId, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Account Balance",
            description = "Create a new balance record for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account balance created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountBalanceDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid balance data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountBalanceDTO>> createBalance(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId,

            @Parameter(description = "Data for the new balance record", required = true,
                    schema = @Schema(implementation = AccountBalanceDTO.class))
            @RequestBody AccountBalanceDTO balanceDTO
    ) {
        return service.createBalance(accountId, balanceDTO)
                .map(createdBalance -> ResponseEntity.status(201).body(createdBalance))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Get Account Balance by ID",
            description = "Retrieve a specific balance record by its unique identifier, ensuring it belongs to the specified account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the account balance",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountBalanceDTO.class))),
            @ApiResponse(responseCode = "404", description = "Account balance not found",
                    content = @Content)
    })
    @GetMapping(value = "/{balanceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountBalanceDTO>> getBalance(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Unique identifier of the balance record", required = true)
            @PathVariable UUID balanceId
    ) {
        return service.getBalance(accountId, balanceId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Update Account Balance",
            description = "Update an existing balance record associated with the specified account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account balance updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountBalanceDTO.class))),
            @ApiResponse(responseCode = "404", description = "Account balance not found",
                    content = @Content)
    })
    @PutMapping(value = "/{balanceId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountBalanceDTO>> updateBalance(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Unique identifier of the balance record to update", required = true)
            @PathVariable UUID balanceId,

            @Parameter(description = "Updated data for the balance record", required = true,
                    schema = @Schema(implementation = AccountBalanceDTO.class))
            @RequestBody AccountBalanceDTO balanceDTO
    ) {
        return service.updateBalance(accountId, balanceId, balanceDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Account Balance",
            description = "Remove an existing balance record by its unique identifier."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account balance deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Account balance not found",
                    content = @Content)
    })
    @DeleteMapping(value = "/{balanceId}")
    public Mono<ResponseEntity<Void>> deleteBalance(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Unique identifier of the balance record to delete", required = true)
            @PathVariable UUID balanceId
    ) {
        return service.deleteBalance(accountId, balanceId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}