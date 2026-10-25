package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

final class AssetPriceActorRules {
    static final String WRITE_SCOPE = "accounts:asset-prices:write";
    private static final Instant MAX_EXPIRY = Instant.parse("9999-12-31T23:59:59Z");

    private AssetPriceActorRules() {}

    static String require(SecurityPrincipal principal, String issuer, Clock clock) {
        if (principal == null
                || !(principal.claims().get("exp") instanceof Instant expiry)
                || !expiry.isAfter(clock.instant())
                || expiry.isAfter(MAX_EXPIRY))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Current asset price identity required");
        var subject = principal.subject();
        if (issuer == null
                || issuer.isBlank()
                || !issuer.equals(principal.issuer())
                || !principal.hasScope(WRITE_SCOPE)
                || subject == null
                || subject.isBlank()
                || subject.codePointCount(0, subject.length()) > 50
                || subject.codePoints()
                        .anyMatch(c -> Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Exact asset price writer required");
        return subject;
    }
}
