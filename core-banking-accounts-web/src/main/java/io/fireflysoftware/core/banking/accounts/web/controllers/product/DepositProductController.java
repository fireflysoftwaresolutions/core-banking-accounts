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


package io.fireflysoftware.core.banking.accounts.web.controllers.product;

import io.fireflysoftware.core.banking.accounts.core.services.product.DepositProductService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.AccountLimitDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.AccountStatementDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.AccountSweepDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.SavingsInterestDTO;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.product.TermDepositDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
@Tag(name = "DepositProduct", description = "Stored statement, limit, interest, term deposit, and sweep")
public class DepositProductController {

    private final DepositProductService service;

    public DepositProductController(DepositProductService service) {
        this.service = service;
    }

    @PostMapping("/statements")
    @Operation(operationId = "recordAccountStatement", summary = "Store an account statement")
    public Mono<ResponseEntity<AccountStatementDTO>> recordStatement(@PathVariable UUID accountId,
                                                                     @RequestBody AccountStatementDTO body,
                                                                     @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.recordStatement(accountId, body).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @GetMapping("/statements/{statementId}")
    @Operation(operationId = "getAccountStatement", summary = "Read an account statement")
    public Mono<ResponseEntity<AccountStatementDTO>> getStatement(@PathVariable UUID accountId,
                                                                  @PathVariable UUID statementId,
                                                                  @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.getStatement(accountId, statementId).map(ResponseEntity::ok);
    }

    @PostMapping("/limits")
    @Operation(operationId = "recordAccountLimit", summary = "Store an account limit check")
    public Mono<ResponseEntity<AccountLimitDTO>> recordLimit(@PathVariable UUID accountId,
                                                             @RequestBody AccountLimitDTO body,
                                                             @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.recordLimit(accountId, body).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @GetMapping("/limits/{limitId}")
    @Operation(operationId = "getAccountLimit", summary = "Read an account limit")
    public Mono<ResponseEntity<AccountLimitDTO>> getLimit(@PathVariable UUID accountId,
                                                          @PathVariable UUID limitId,
                                                          @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.getLimit(accountId, limitId).map(ResponseEntity::ok);
    }

    @PostMapping("/savings-interest")
    @Operation(operationId = "recordSavingsInterest", summary = "Store savings interest")
    public Mono<ResponseEntity<SavingsInterestDTO>> recordInterest(@PathVariable UUID accountId,
                                                                   @RequestBody SavingsInterestDTO body,
                                                                   @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.recordInterest(accountId, body).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @GetMapping("/savings-interest/{interestId}")
    @Operation(operationId = "getSavingsInterest", summary = "Read savings interest")
    public Mono<ResponseEntity<SavingsInterestDTO>> getInterest(@PathVariable UUID accountId,
                                                                @PathVariable UUID interestId,
                                                                @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.getInterest(accountId, interestId).map(ResponseEntity::ok);
    }

    @PostMapping("/term-deposits")
    @Operation(operationId = "recordTermDeposit", summary = "Store a term-deposit payout")
    public Mono<ResponseEntity<TermDepositDTO>> recordTermDeposit(@PathVariable UUID accountId,
                                                                  @RequestBody TermDepositDTO body,
                                                                  @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.recordTermDeposit(accountId, body).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @GetMapping("/term-deposits/{termDepositId}")
    @Operation(operationId = "getTermDeposit", summary = "Read a term-deposit payout")
    public Mono<ResponseEntity<TermDepositDTO>> getTermDeposit(@PathVariable UUID accountId,
                                                               @PathVariable UUID termDepositId,
                                                               @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.getTermDeposit(accountId, termDepositId).map(ResponseEntity::ok);
    }

    @PostMapping("/sweeps")
    @Operation(operationId = "recordAccountSweep", summary = "Store a space sweep")
    public Mono<ResponseEntity<AccountSweepDTO>> recordSweep(@PathVariable UUID accountId,
                                                             @RequestBody AccountSweepDTO body,
                                                             @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.recordSweep(accountId, body).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @GetMapping("/sweeps/{sweepId}")
    @Operation(operationId = "getAccountSweep", summary = "Read a space sweep")
    public Mono<ResponseEntity<AccountSweepDTO>> getSweep(@PathVariable UUID accountId,
                                                          @PathVariable UUID sweepId,
                                                          @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.getSweep(accountId, sweepId).map(ResponseEntity::ok);
    }
}
