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


package io.fireflysoftware.core.banking.accounts.web.controllers.notification;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.core.services.notification.AccountNotificationService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.notification.AccountNotificationDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.enums.notification.NotificationTypeEnum;
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

@Tag(name = "Account Notifications", description = "APIs for managing account notifications and alerts")
@RestController
@RequestMapping("/api/v1/account-notifications")
public class AccountNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(AccountNotificationController.class);

    @Autowired
    private AccountNotificationService service;

    /**
     * Common error handling method for controller endpoints
     * @param e The exception that occurred
     * @param <T> The type of response entity
     * @return A response entity with appropriate status code and error message
     */
    private <T> Mono<ResponseEntity<T>> handleError(Throwable e) {
        logger.error("Error in AccountNotificationController: {}", e.getMessage());

        if (e instanceof IllegalArgumentException) {
            return Mono.just(ResponseEntity.badRequest().build());
        } else if (e instanceof IllegalStateException) {
            return Mono.just(ResponseEntity.badRequest().build());
        } else {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @Operation(
            summary = "Filter Account Notifications",
            description = "Retrieve a paginated list of all account notifications based on filter criteria."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the notifications",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "404", description = "No notifications found",
                    content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AccountNotificationDTO>>> filterAccountNotifications(
            @RequestBody FilterRequest<AccountNotificationDTO> filterRequest
    ) {
        return service.listAccountNotifications(filterRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Create Account Notification",
            description = "Create a new account notification with the specified details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully created the notification",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountNotificationDTO>> createAccountNotification(
            @Parameter(description = "Data for the new account notification", required = true,
                    schema = @Schema(implementation = AccountNotificationDTO.class))
            @RequestBody AccountNotificationDTO accountNotificationDTO
    ) {
        return service.createAccountNotification(accountNotificationDTO)
                .map(createdNotification -> ResponseEntity.status(201).body(createdNotification))
                .onErrorResume(this::handleError);
    }

    @Operation(
            summary = "Get Account Notification by ID",
            description = "Retrieve an existing account notification by its unique identifier."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the notification",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content)
    })
    @GetMapping(value = "/{accountNotificationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountNotificationDTO>> getAccountNotification(
            @Parameter(description = "Unique identifier of the account notification", required = true)
            @PathVariable("accountNotificationId") UUID accountNotificationId
    ) {
        return service.getAccountNotification(accountNotificationId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving account notification {}: {}", accountNotificationId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @Operation(
            summary = "Update Account Notification",
            description = "Update an existing account notification with the specified details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated the notification",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @PutMapping(value = "/{accountNotificationId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountNotificationDTO>> updateAccountNotification(
            @Parameter(description = "Unique identifier of the account notification to update", required = true)
            @PathVariable("accountNotificationId") UUID accountNotificationId,

            @Parameter(description = "Updated data for the account notification", required = true,
                    schema = @Schema(implementation = AccountNotificationDTO.class))
            @RequestBody AccountNotificationDTO accountNotificationDTO
    ) {
        return service.updateAccountNotification(accountNotificationId, accountNotificationDTO)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error updating account notification {}: {}", accountNotificationId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    @Operation(
            summary = "Delete Account Notification",
            description = "Delete an existing account notification."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted the notification"),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content)
    })
    @DeleteMapping(value = "/{accountNotificationId}")
    public Mono<ResponseEntity<Void>> deleteAccountNotification(
            @Parameter(description = "Unique identifier of the account notification to delete", required = true)
            @PathVariable("accountNotificationId") UUID accountNotificationId
    ) {
        return service.deleteAccountNotification(accountNotificationId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    logger.error("Error deleting account notification {}: {}", accountNotificationId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().<Void>build());
                    }
                    return Mono.just(ResponseEntity.internalServerError().<Void>build());
                });
    }

    @Operation(
            summary = "Get Account Notifications by Account ID",
            description = "Retrieve all notifications for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the notifications",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid account ID",
                    content = @Content)
    })
    @GetMapping(value = "/account/{accountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<AccountNotificationDTO>>> getAccountNotificationsByAccountId(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return Mono.just(service.getAccountNotificationsByAccountId(accountId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving notifications for account {}: {}", accountId, e.getMessage());
                    return handleError(e);
                });
    }

    @Operation(
            summary = "Get Unread Account Notifications",
            description = "Retrieve all unread notifications for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the unread notifications",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid account ID",
                    content = @Content)
    })
    @GetMapping(value = "/account/{accountId}/unread", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<AccountNotificationDTO>>> getUnreadAccountNotifications(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return Mono.just(service.getUnreadAccountNotifications(accountId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving unread notifications for account {}: {}", accountId, e.getMessage());
                    return handleError(e);
                });
    }

    @Operation(
            summary = "Get Account Notifications by Type",
            description = "Retrieve all notifications of a specific type for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the notifications",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @GetMapping(value = "/account/{accountId}/type/{notificationType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<AccountNotificationDTO>>> getAccountNotificationsByType(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId,

            @Parameter(description = "Type of notification", required = true)
            @PathVariable("notificationType") NotificationTypeEnum notificationType
    ) {
        return Mono.just(service.getAccountNotificationsByType(accountId, notificationType))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving notifications of type {} for account {}: {}", 
                            notificationType, accountId, e.getMessage());
                    return handleError(e);
                });
    }

    @Operation(
            summary = "Mark Notification as Read",
            description = "Mark an account notification as read."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully marked the notification as read",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Notification already read",
                    content = @Content)
    })
    @PostMapping(value = "/{accountNotificationId}/mark-read", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AccountNotificationDTO>> markNotificationAsRead(
            @Parameter(description = "Unique identifier of the account notification", required = true)
            @PathVariable("accountNotificationId") UUID accountNotificationId
    ) {
        return service.markNotificationAsRead(accountNotificationId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error marking notification {} as read: {}", accountNotificationId, e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    } else if (e instanceof IllegalStateException) {
                        return Mono.just(ResponseEntity.badRequest().build());
                    }
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @Operation(
            summary = "Mark All Notifications as Read",
            description = "Mark all notifications for a specific account as read."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully marked all notifications as read",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "integer", format = "int64"))),
            @ApiResponse(responseCode = "400", description = "Invalid account ID",
                    content = @Content)
    })
    @PostMapping(value = "/account/{accountId}/mark-all-read", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Long>> markAllNotificationsAsRead(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return service.markAllNotificationsAsRead(accountId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error marking all notifications as read for account {}: {}", accountId, e.getMessage());
                    return handleError(e);
                });
    }

    @Operation(
            summary = "Get Active Notifications",
            description = "Retrieve all active (non-expired) notifications for a specific account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved the active notifications",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AccountNotificationDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid account ID",
                    content = @Content)
    })
    @GetMapping(value = "/account/{accountId}/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Flux<AccountNotificationDTO>>> getActiveNotifications(
            @Parameter(description = "Unique identifier of the account", required = true)
            @PathVariable("accountId") UUID accountId
    ) {
        return Mono.just(service.getActiveNotifications(accountId))
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    logger.error("Error retrieving active notifications for account {}: {}", accountId, e.getMessage());
                    return handleError(e);
                });
    }
}
