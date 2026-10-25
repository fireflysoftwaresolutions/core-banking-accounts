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

package io.fireflysoftware.core.banking.accounts.web.controllers.funds;

import io.fireflysoftware.core.banking.accounts.core.services.funds.AccountFundsService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@io.swagger.v3.oas.annotations.tags.Tag(name = "AccountFunds")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "fundsBearer")
@io.swagger.v3.oas.annotations.security.SecurityScheme(
        name = "fundsBearer",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
@RestController
@RequestMapping("/api/v1/tenants/{tenant}/funds/accounts/{accountId}")
public class AccountFundsController {
    private final AccountFundsService service;

    public AccountFundsController(AccountFundsService service) {
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "beginFundsEnrollment")
    @PostMapping("/enrollment")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Enrollment> begin(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId,
            @RequestBody Begin input) {
        return service.begin(principal(jwt), tenant, accountId, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "linkFundsEnrollment")
    @PostMapping("/enrollment/link")
    public Mono<Enrollment> link(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId,
            @RequestBody Link input) {
        return service.link(principal(jwt), tenant, accountId, input);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "verifyFundsEnrollment")
    @GetMapping("/enrollment")
    public Mono<Enrollment> verify(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId) {
        return service.verify(principal(jwt), tenant, accountId);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "controlAccountFunds")
    @PostMapping("/controls")
    public Mono<Enrollment> control(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal
                    Jwt jwt,
            @PathVariable String tenant,
            @PathVariable UUID accountId,
            @RequestBody Manage input) {
        return service.control(principal(jwt), tenant, accountId, input);
    }

    static SecurityPrincipal principal(Jwt jwt) {
        if (jwt == null) return null;
        String scope = jwt.getClaimAsString("scope");
        Set<String> scopes =
                scope == null
                        ? Set.of()
                        : Arrays.stream(scope.split(" "))
                                .filter(s -> !s.isBlank())
                                .collect(Collectors.toSet());
        return SecurityPrincipal.builder()
                .subject(jwt.getSubject())
                .issuer(jwt.getIssuer().toString())
                .tenantId(jwt.getClaimAsString("tenant_id"))
                .scopes(scopes)
                .claims(jwt.getClaims())
                .build();
    }
}
