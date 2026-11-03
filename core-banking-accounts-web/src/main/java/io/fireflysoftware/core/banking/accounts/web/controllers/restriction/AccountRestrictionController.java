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


package io.fireflysoftware.core.banking.accounts.web.controllers.restriction;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.services.restriction.AccountRestrictionService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.restriction.AccountRestrictionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Tag(name = "Account Restrictions", description = "APIs for managing restrictions placed on accounts")
@RestController
@RequestMapping("/api/v1/account-restrictions")
public class AccountRestrictionController {

    private static final Logger logger = LoggerFactory.getLogger(AccountRestrictionController.class);

    @Autowired
    private AccountRestrictionService service;

    /**
     * Common error handling method for controller endpoints
     * @param e The exception that occurred
     * @param <T> The type of response entity
     * @return A response entity with appropriate status code and error message
     */
    private <T> Mono<ResponseEntity<T>> handleError(Throwable e) {
        logger.error("Error in AccountRestrictionController: {}", e.getMessage());

        if (e instanceof IllegalArgumentException) {
            return Mono.just(ResponseEntity.badRequest().build());
        } else if (e instanceof IllegalStateException) {
            return Mono.just(ResponseEntity.badRequest().build());
        } else {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @Operation(
            summary = "Filter Account Restrictions",
            description = "Retrieve a paginated list of all account restrictions based on filter criteria."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the restrictions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No restrictions found",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AccountRestrictionDTO>>> filterAccountRestrictions(
            @RequestBody FilterRequest<AccountRestrictionDTO> filterRequest
    ) {
        return service.listAccountRestrictions(filterRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Account Restriction",
            description = "Create a new account restriction with the specified details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully created the restriction",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountRestrictionDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountRestrictionDTO>> createAccountRestriction(
            @Parameter(description = "Data for the new account restriction", required = true,
                    schema = @Schema(implementation = AccountRestrictionDTO.class))
            @RequestBody AccountRestrictionDTO accountRestrictionDTO
    ) {
        return service.createAccountRestriction(accountRestrictionDTO)
                .map(createdRestriction -> ResponseEntity.status(201).body(createdRestriction))
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Get Account Restriction by ID",
            description = "Retrieve an existing account restriction by its unique identifier."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the restriction",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountRestrictionDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restriction not found",
                    content = @Content)
    })
    @GetMapping(value = "/{accountRestrictionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountRestrictionDTO>> getAccountRestriction(
            @Parameter(description = "Unique identifier of the account restriction", required = true)
            @PathVariable("accountRestrictionId") UUID accountRestrictionId
    ) {
        return service.getAccountRestriction(accountRestrictionId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving account restriction {}: {}", accountRestrictionId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @Operation(
            summary = "Update Account Restriction",
            description = "Update an existing account restriction with the specified details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated the restriction",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountRestrictionDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restriction not found",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @PutMapping(value = "/{accountRestrictionId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountRestrictionDTO>> updateAccountRestriction(
            @Parameter(description = "Unique identifier of the account restriction to update", required = true)
            @PathVariable("accountRestrictionId") UUID accountRestrictionId,

            @Parameter(description = "Updated data for the account restriction", required = true,
                    schema = @Schema(implementation = AccountRestrictionDTO.class))
            @RequestBody AccountRestrictionDTO accountRestrictionDTO
    ) {
        return service.updateAccountRestriction(accountRestrictionId, accountRestrictionDTO)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error updating account restriction {}: {}", accountRestrictionId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Operation(
            summary = "Delete Account Restriction",
            description = "Delete an existing account restriction."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted the restriction"),
            @ApiResponse(responseCode = "404", description = "Restriction not found",
                    content = @Content)
    })
    @DeleteMapping(value = "/{accountRestrictionId}")
    public Mono<ResponseEntity<Void>> deleteAccountRestriction(
            @Parameter(description = "Unique identifier of the account restriction to delete", required = true)
            @PathVariable("accountRestrictionId") UUID accountRestrictionId
    ) {
        return service.deleteAccountRestriction(accountRestrictionId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    logger.error("Error deleting account restriction {}: {}", accountRestrictionId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().<Void>build());
                    }
                    return Mono.just(ResponseEntity.internalServerError().<Void>build());
                });
    }

    @Operation(
            summary = "Get Account Restrictions by Account ID",
            description = "Retrieve all restrictions for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the restrictions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountRestrictionDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid account ID",
                    content = @Content)
    })
    @GetMapping(value = "/account/{accountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<AccountRestrictionDTO>>> getAccountRestrictionsByAccountId(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return Mono.just(service.getAccountRestrictionsByAccountId(accountId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving restrictions for account {}: {}", accountId, e.getMessage());
                    return handleError(e);
                });
    }

    @Operation(
            summary = "Get Active Account Restrictions by Account ID",
            description = "Retrieve all active restrictions for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the active restrictions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountRestrictionDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid account ID",
                    content = @Content)
    })
    @GetMapping(value = "/account/{accountId}/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<AccountRestrictionDTO>>> getActiveAccountRestrictionsByAccountId(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return Mono.just(service.getActiveAccountRestrictionsByAccountId(accountId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving active restrictions for account {}: {}", accountId, e.getMessage());
                    return handleError(e);
                });
    }

    @Operation(
            summary = "Remove Restriction",
            description = "Mark an account restriction as inactive."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully removed the restriction",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountRestrictionDTO.class))),
            @ApiResponse(responseCode = "404", description = "Restriction not found",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input or restriction already removed",
                    content = @Content)
    })
    @PostMapping(value = "/{accountRestrictionId}/remove", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountRestrictionDTO>> removeRestriction(
            @Parameter(description = "Unique identifier of the account restriction", required = true)
            @PathVariable("accountRestrictionId") UUID accountRestrictionId,

            @Parameter(description = "User or system that removed the restriction", required = true)
            @RequestParam("removedBy") String removedBy
    ) {
        return service.removeRestriction(accountRestrictionId, removedBy)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error removing restriction {}: {}", accountRestrictionId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    } else if (e instanceof IllegalStateException) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}
