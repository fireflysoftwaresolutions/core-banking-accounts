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


package io.fireflysoftware.core.banking.accounts.web.controllers.status;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.services.status.AccountStatusHistoryService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.status.AccountStatusHistoryDTO;
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

@Tag(name = "Account Status History", description = "APIs for managing the status history of a specific bank account")
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/status-history")
public class AccountStatusHistoryController {

    @Autowired
    private AccountStatusHistoryService service;

    @Operation(
            summary = "List Account Status History",
            description = "Retrieve a paginated list of status history records associated with the specified account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved status history records",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No status history records found for the specified account",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AccountStatusHistoryDTO>>> getAllStatusHistory(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @ParameterObject
            @ModelAttribute PaginationRequest paginationRequest
    ) {
        return service.listStatusHistory(accountId, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Account Status History",
            description = "Create a new status history record for the specified account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Status history record created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountStatusHistoryDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status history data provided",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountStatusHistoryDTO>> createStatusHistory(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Data for the new account status history record", required = true,
                    schema = @Schema(implementation = AccountStatusHistoryDTO.class))
            @RequestBody AccountStatusHistoryDTO historyDTO
    ) {
        return service.createStatusHistory(accountId, historyDTO)
                .map(createdHistory -> ResponseEntity.status(201).body(createdHistory))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Get Account Status History by ID",
            description = "Retrieve a specific status history record by its unique identifier, ensuring it belongs to the specified account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the account status history",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountStatusHistoryDTO.class))),
            @ApiResponse(responseCode = "404", description = "Status history record not found",
                    content = @Content)
    })
    @GetMapping(value = "/{historyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountStatusHistoryDTO>> getStatusHistory(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Unique identifier of the status history record", required = true)
            @PathVariable UUID historyId
    ) {
        return service.getStatusHistory(accountId, historyId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Update Account Status History",
            description = "Update an existing status history record associated with the specified account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status history record updated successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountStatusHistoryDTO.class))),
            @ApiResponse(responseCode = "404", description = "Status history record not found",
                    content = @Content)
    })
    @PutMapping(value = "/{historyId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountStatusHistoryDTO>> updateStatusHistory(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Unique identifier of the status history record to update", required = true)
            @PathVariable UUID historyId,

            @Parameter(description = "Updated data for the status history record", required = true,
                    schema = @Schema(implementation = AccountStatusHistoryDTO.class))
            @RequestBody AccountStatusHistoryDTO historyDTO
    ) {
        return service.updateStatusHistory(accountId, historyId, historyDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Account Status History",
            description = "Remove an existing status history record by its unique identifier."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Status history record deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Status history record not found",
                    content = @Content)
    })
    @DeleteMapping(value = "/{historyId}")
    public Mono<ResponseEntity<Void>> deleteStatusHistory(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable UUID accountId,

            @Parameter(description = "Unique identifier of the status history record to delete", required = true)
            @PathVariable UUID historyId
    ) {
        return service.deleteStatusHistory(accountId, historyId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}