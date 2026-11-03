/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.controllers.card.enrollment;

import io.fireflysoftware.core.banking.accounts.core.services.card.enrollment.*;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.card.enrollment.AccountCardEnrollmentContracts.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(value = "/api/v1/accounts/{accountId}/card-enrollments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "AccountCardEnrollment", description = "Account-owned pending card enrollment admission and recovery")
@SecurityRequirement(name = "accountCardEnrollmentBearer")
@SecurityScheme(name = "accountCardEnrollmentBearer", type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class AccountCardFenceController {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AccountCardFenceController.class);
    private final ObjectProvider<AccountCardFenceService> services;
    public AccountCardFenceController(ObjectProvider<AccountCardFenceService> services) { this.services = services; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @DisableIdempotency
    @Operation(operationId = "acquireAccountCardEnrollment", summary = "Acquire a pending account card enrollment fence",
            description = "Uses the explicit original action and current stored owner mandate. This receipt does not assert card issuance.")
    public Mono<ResponseEntity<Observation>> acquire(@PathVariable UUID accountId, @RequestBody Acquire request) {
        return Mono.defer(() -> service().acquire(accountId, request)).map(this::response);
    }

    @PostMapping(value = "/{actionId}/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    @DisableIdempotency
    @Operation(operationId = "claimAccountCardEnrollment", summary = "Claim the original fence before downstream card coordination",
            description = "Requires a designated Cards client and current original eligibility. Claim prevents unproven customer cancellation.")
    public Mono<ResponseEntity<Observation>> claim(@PathVariable UUID accountId, @PathVariable UUID actionId, @RequestBody Claim request) {
        return Mono.defer(() -> service().claim(accountId, actionId, request)).map(this::response);
    }

    @PostMapping(value = "/{actionId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @DisableIdempotency
    @Operation(operationId = "cancelAccountCardEnrollment", summary = "Cancel a held fence before the Cards claim barrier",
            description = "Expired HELD fences can be cancelled under fresh authority. CLAIMED or uncertain downstream work is never released by timeout.")
    public Mono<ResponseEntity<Observation>> cancel(@PathVariable UUID accountId, @PathVariable UUID actionId, @RequestBody Cancel request) {
        return Mono.defer(() -> service().cancel(accountId, actionId, request)).map(this::response);
    }

    @GetMapping("/{actionId}/operations/{operationActionId}")
    @DisableIdempotency
    @Operation(operationId = "recoverAccountCardEnrollment", summary = "Read the original fence and operation receipt",
            description = "Read-only recovery retains original actions, receipts and deadline. It never creates, claims, cancels, renews or dispatches anything.")
    public Mono<ResponseEntity<Observation>> recover(@PathVariable UUID accountId, @PathVariable UUID actionId, @PathVariable UUID operationActionId) {
        return Mono.defer(() -> service().recover(accountId, actionId, operationActionId)).map(this::response);
    }

    public record Failure(UUID operationActionId, AccountCardFenceException.Reason reason) {}
    public record InvalidRequest(String code) {}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<InvalidRequest> invalidRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new InvalidRequest("EXPLICIT_ACCOUNT_CARD_ENROLLMENT_REQUEST_REQUIRED"));
    }
    @ExceptionHandler(AccountCardFenceException.class)
    ResponseEntity<Failure> failure(AccountCardFenceException error) {
        LOG.warn("Account card enrollment failure: reason={}, facts={}, cleanup={}, causes={}",
                error.reason().name(), error.transactionFacts().toString(), error.cleanupFact().name(), AccountCardFenceFailureDiagnostics.causes(error).toString());
        HttpStatus status = switch (error.reason()) {
            case CONFLICT -> HttpStatus.CONFLICT;
            case REFUSED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAVAILABLE, UNKNOWN, WITHHELD -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new Failure(error.operationActionId(), error.reason()));
    }
    private ResponseEntity<Observation> response(Observation value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    private AccountCardFenceService service() {
        var service = services.getIfAvailable();
        if (service == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account card enrollment capability is not configured");
        return service;
    }
}
