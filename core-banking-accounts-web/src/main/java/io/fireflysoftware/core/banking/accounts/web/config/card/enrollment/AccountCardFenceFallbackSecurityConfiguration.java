/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.config.card.enrollment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "firefly.security.resource-server.enabled", havingValue = "false", matchIfMissing = true)
public class AccountCardFenceFallbackSecurityConfiguration {
    @Bean @Order(-101)
    SecurityWebFilterChain accountCardFenceUnconfiguredReceiver(ServerHttpSecurity http) {
        return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/accounts/*/card-enrollments", "/api/v1/accounts/*/card-enrollments/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable).authorizeExchange(rules -> rules.anyExchange().denyAll()).build();
    }
}
