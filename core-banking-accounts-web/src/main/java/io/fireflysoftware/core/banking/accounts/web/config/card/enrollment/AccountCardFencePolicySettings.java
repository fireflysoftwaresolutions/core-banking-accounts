/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.config.card.enrollment;

import java.net.URI;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("firefly.accounts.card-enrollment.opa")
public record AccountCardFencePolicySettings(boolean enabled, URI endpoint, String decisionPath,
        String evaluationPath, boolean allowLoopbackHttp) {
    public AccountCardFencePolicySettings {
        if (enabled) {
            if (endpoint == null || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                    || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null || !endpoint.equals(endpoint.normalize())
                    || !Set.of("", "/").contains(endpoint.getRawPath())
                    || !("https".equals(endpoint.getScheme()) || allowLoopbackHttp && "http".equals(endpoint.getScheme())
                        && Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(endpoint.getHost())))
                throw new IllegalArgumentException("Explicit trusted account card fence policy endpoint required");
            for (String path : new String[] {decisionPath, evaluationPath})
                if (path == null || !path.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))
                    throw new IllegalArgumentException("Exact account card fence policy document paths required");
        }
    }
}
