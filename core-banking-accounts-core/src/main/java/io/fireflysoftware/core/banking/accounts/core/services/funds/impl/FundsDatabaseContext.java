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

package io.fireflysoftware.core.banking.accounts.core.services.funds.impl;

import static io.fireflysoftware.core.banking.accounts.core.services.funds.impl.AccountFundsServiceImpl.fail;

import io.fireflysoftware.core.banking.accounts.models.repositories.funds.FundsNativeRepository;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public final class FundsDatabaseContext {
    private final FundsNativeRepository nativeRepository;

    public FundsDatabaseContext(FundsNativeRepository nativeRepository) {
        this.nativeRepository = nativeRepository;
    }

    Mono<Void> establish(SecurityPrincipal p, String tenant, String purpose, UUID... accounts) {
        String scope =
                switch (purpose) {
                    case "ENROLL" -> "funds:enroll";
                    case "LINK" -> "funds:link";
                    case "VERIFY" -> "funds:verify";
                    case "CONTROL" -> "funds:control";
                    default -> throw fail(403, "SERVER_FUNDS_PURPOSE_REQUIRED");
                };
        FundsPolicy.require(p, tenant, scope, accounts);
        if (accounts.length < 1 || accounts.length > 1000)
            return Mono.error(fail(403, "BOUNDED_FUNDS_RESOURCES_REQUIRED"));
        String resourceJson =
                Arrays.stream(accounts)
                        .distinct()
                        .map(a -> "\"" + a + "\"")
                        .collect(Collectors.joining(",", "[", "]"));
        return nativeRepository
                .runtimeAllowed()
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(fail(503, "NONPRIVILEGED_FUNDS_RUNTIME_REQUIRED")))
                .then(nativeRepository.establishContext(tenant, resourceJson, purpose));
    }
}
