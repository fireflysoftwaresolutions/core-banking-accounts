/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.config.card.enrollment;

import org.fireflyframework.security.rs.ResourceServerAutoConfiguration;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

/** Existing Funds and Asset Price chains suppress the shared fallback chain; this receiver owns only fence routes. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "firefly.security.resource-server.enabled", havingValue = "true")
public class AccountCardFenceReceiverConfiguration {
    @Bean @Order(-101)
    SecurityWebFilterChain accountCardFenceNativeReceiver(ServerHttpSecurity http,
            ResourceServerAutoConfiguration original, ResourceServerProperties properties,
            @Qualifier("fireflyReactiveJwtDecoder") ReactiveJwtDecoder decoder,
            @Qualifier("fireflyJwtAuthenticationConverter") Converter<Jwt, Mono<AbstractAuthenticationToken>> converter,
            PolicyDecisionPort policies) {
        if (!properties.isStrictPolicy()) throw new IllegalArgumentException("Strict native account card enrollment policy is required");
        http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/accounts/*/card-enrollments", "/api/v1/accounts/*/card-enrollments/**"));
        return original.fireflySecurityWebFilterChain(http, decoder, converter, policies, properties);
    }
}
