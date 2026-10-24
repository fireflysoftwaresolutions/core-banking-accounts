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

import io.fireflysoftware.core.banking.accounts.core.services.funds.LedgerFundsPort;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;
import io.fireflysoftware.core.banking.ledger.sdk.api.LedgerFundsApi;
import io.fireflysoftware.core.banking.ledger.sdk.invoker.ApiClient;
import io.fireflysoftware.core.banking.ledger.sdk.model.Account;

import org.fireflyframework.client.oauth2.OAuth2ClientHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
public final class SdkLedgerFundsAdapter implements LedgerFundsPort {
    private final OAuth2ClientHelper oauth;
    private final String baseUrl, scopes;

    public SdkLedgerFundsAdapter(
            @Value("${funds.ledger.base-url:}") String baseUrl,
            @Value("${funds.ledger.token-uri:}") String tokenUri,
            @Value("${funds.ledger.client-id:}") String clientId,
            @Value("${funds.ledger.client-secret:}") String clientSecret,
            @Value("${funds.ledger.scopes:}") String scopes,
            @Value("${funds.ledger.allow-loopback-http:false}") boolean loopback) {
        this.baseUrl = baseUrl;
        this.scopes = scopes;
        oauth =
                valid(baseUrl, loopback)
                                && valid(tokenUri, loopback)
                                && !clientId.isBlank()
                                && !clientSecret.isBlank()
                                && !scopes.isBlank()
                        ? new OAuth2ClientHelper(
                                tokenUri,
                                clientId,
                                clientSecret,
                                OAuth2ClientHelper.OAuth2Config.builder()
                                        .timeout(Duration.ofSeconds(4))
                                        .enableRetry(false)
                                        .build())
                        : null;
    }

    @Override
    public Mono<Void> apply(String tenant, Enrollment enrollment, UUID actionId, Control target) {
        return Mono.defer(
                        () -> {
                            if (oauth == null)
                                return Mono.error(fail(503, "LEDGER_FUNDS_CONTROL_NOT_CONFIGURED"));
                            if (target == null
                                    || !Set.of(Control.ACTIVE, Control.BLOCKED, Control.CLOSED)
                                            .contains(target))
                                return Mono.error(
                                        fail(400, "EXPLICIT_ACCOUNT_MONEY_CONTROL_REQUIRED"));
                            return oauth.getClientCredentialsToken(scopes)
                                    .filter(token -> !token.isBlank())
                                    .switchIfEmpty(
                                            Mono.error(
                                                    fail(503, "LEDGER_SERVICE_TOKEN_UNAVAILABLE")))
                                    .flatMap(
                                            token -> {
                                                var web =
                                                        ApiClient.buildWebClientBuilder()
                                                                .codecs(
                                                                        c ->
                                                                                c.defaultCodecs()
                                                                                        .maxInMemorySize(
                                                                                                1024
                                                                                                        * 1024))
                                                                .build();
                                                var client =
                                                        new ApiClient(web).setBasePath(baseUrl);
                                                client.addDefaultHeader(
                                                        "Authorization", "Bearer " + token);
                                                var api = new LedgerFundsApi(client);
                                                if (target != Control.CLOSED)
                                                    return send(
                                                                    api,
                                                                    tenant,
                                                                    enrollment,
                                                                    actionId,
                                                                    target,
                                                                    false)
                                                            .then();
                                                UUID block =
                                                        UUID.nameUUIDFromBytes(
                                                                ("account-funds-close-block:"
                                                                                + tenant
                                                                                + ":"
                                                                                + enrollment
                                                                                        .accountId()
                                                                                + ":"
                                                                                + actionId)
                                                                        .getBytes(
                                                                                StandardCharsets
                                                                                        .UTF_8));
                                                return send(
                                                                api,
                                                                tenant,
                                                                enrollment,
                                                                block,
                                                                Control.BLOCKED,
                                                                true)
                                                        .then(
                                                                send(
                                                                                api,
                                                                                tenant,
                                                                                enrollment,
                                                                                actionId,
                                                                                Control.CLOSED,
                                                                                false)
                                                                        .onErrorMap(
                                                                                WebClientResponseException
                                                                                        .class,
                                                                                error ->
                                                                                        closureRejected(
                                                                                                        error)
                                                                                                ? new ClosureRejected()
                                                                                                : fail(
                                                                                                        503,
                                                                                                        "LEDGER_FUNDS_CONTROL_UNAVAILABLE")))
                                                        .then();
                                            })
                                    .timeout(Duration.ofSeconds(10));
                        })
                .onErrorMap(
                        error ->
                                error instanceof ClosureRejected
                                                || error instanceof ResponseStatusException
                                        ? error
                                        : fail(503, "LEDGER_FUNDS_CONTROL_UNAVAILABLE"));
    }

    private Mono<Account> send(
            LedgerFundsApi api,
            String tenant,
            Enrollment enrollment,
            UUID action,
            Control target,
            boolean allowAlreadyClosed) {
        var request =
                new io.fireflysoftware.core.banking.ledger.sdk.model.Control()
                        .actionId(action)
                        .state(
                                io.fireflysoftware.core.banking.ledger.sdk.model.Control.StateEnum
                                        .fromValue(target.name()));
        return api.controlLedgerFunds(tenant, enrollment.accountId(), request, null)
                .switchIfEmpty(Mono.error(fail(502, "INCOMPLETE_LEDGER_FUNDS_CONTROL")))
                .map(
                        found -> {
                            if (!enrollment.accountId().equals(found.getAccountId())
                                    || !enrollment.enrollmentId().equals(found.getEnrollmentId())
                                    || !enrollment.contractId().equals(found.getContractId())
                                    || !enrollment.currency().equals(found.getCurrency())
                                    || found.getKind() == null
                                    || !enrollment.kind().name().equals(found.getKind().getValue())
                                    || found.getState() == null
                                    || (!target.name().equals(found.getState().getValue())
                                            && !(allowAlreadyClosed
                                                    && "CLOSED"
                                                            .equals(found.getState().getValue()))))
                                throw fail(502, "LEDGER_FUNDS_CONTROL_METADATA_MISMATCH");
                            return found;
                        });
    }

    private boolean closureRejected(WebClientResponseException error) {
        if (error.getStatusCode().value() != 409) return false;
        try {
            return "CLOSURE_REQUIRES_BLOCKED_ZERO_FUNDS"
                    .equals(
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(error.getResponseBodyAsString())
                                    .path("message")
                                    .asText());
        } catch (java.io.IOException parseError) {
            return false;
        }
    }

    private static boolean valid(String value, boolean loopback) {
        try {
            var uri = URI.create(value);
            return uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && ("https".equals(uri.getScheme())
                            || (loopback
                                    && "http".equals(uri.getScheme())
                                    && Set.of("localhost", "127.0.0.1", "::1", "[::1]")
                                            .contains(uri.getHost())));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
