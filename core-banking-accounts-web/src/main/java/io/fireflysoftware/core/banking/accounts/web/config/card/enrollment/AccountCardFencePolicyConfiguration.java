/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.config.card.enrollment;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.fireflyframework.security.adapter.opa.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "firefly.accounts.card-enrollment.opa.enabled", havingValue = "true")
@EnableConfigurationProperties(AccountCardFencePolicySettings.class)
public class AccountCardFencePolicyConfiguration {
    private static final Duration DEADLINE = Duration.ofSeconds(2);
    @Bean @ConditionalOnMissingBean(PolicyDecisionPort.class)
    PolicyDecisionPort accountCardFencePolicyDecision(AccountCardFencePolicySettings settings) {
        var adapter = new OpaPolicyDecisionAdapter(transport(settings), settings.decisionPath());
        return (principal, action, resource, context) -> adapter.authorize(principal, action, resource, context)
                .timeout(DEADLINE).onErrorReturn(Decision.indeterminate("Account card fence policy unavailable"));
    }
    @Bean @ConditionalOnMissingBean(PolicyEvaluationPort.class)
    PolicyEvaluationPort accountCardFencePolicyEvaluation(AccountCardFencePolicySettings settings) {
        var adapter = new OpaPolicyEvaluationAdapter(transport(settings), settings.evaluationPath());
        return (principal, action, resource, context) -> adapter.evaluate(principal, action, resource, context)
                .timeout(DEADLINE).onErrorReturn(new PolicyEvaluation(Decision.indeterminate("Account card fence policy unavailable"), Optional.empty()));
    }
    private static WebClient transport(AccountCardFencePolicySettings settings) {
        var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(DEADLINE).build();
        return WebClient.builder().baseUrl(settings.endpoint().toASCIIString()).clientConnector(new JdkClientHttpConnector(http))
                .codecs(codecs -> { codecs.defaultCodecs().maxInMemorySize(16 * 1024);codecs.defaultCodecs().enableLoggingRequestDetails(false); }).build();
    }
}
