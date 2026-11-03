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


package io.fireflysoftware.core.banking.accounts.web.controllers.position;

import io.fireflysoftware.core.banking.accounts.core.services.position.CustomerPositionService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.position.CustomerPositionDTO;
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
@RequestMapping("/api/v1/customer-positions")
@Tag(name = "CustomerPosition", description = "Stored customer position")
public class CustomerPositionController {

    private final CustomerPositionService service;

    public CustomerPositionController(CustomerPositionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(operationId = "recordCustomerPosition", summary = "Store a customer position")
    public Mono<ResponseEntity<CustomerPositionDTO>> record(@RequestBody CustomerPositionDTO body,
                                                            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.record(body).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @GetMapping("/{positionId}")
    @Operation(operationId = "getCustomerPosition", summary = "Read a stored customer position")
    public Mono<ResponseEntity<CustomerPositionDTO>> get(@PathVariable UUID positionId,
                                                         @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.get(positionId).map(ResponseEntity::ok);
    }
}
