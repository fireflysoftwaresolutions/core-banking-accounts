package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import org.fireflyframework.security.rs.AccessTokenValidators;
import org.fireflyframework.security.webflux.authentication.PrincipalSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * The configured issuer defines the stored subject namespace; changing it requires identity
 * migration.
 */
@Configuration(proxyBeanMethods = false)
public class AssetPriceSecurityConfiguration {
    @Bean
    @Order(-90)
    SecurityWebFilterChain assetPriceWriteSecurity(
            ServerHttpSecurity http,
            @Qualifier("fireflyJwtAuthenticationConverter")
                    Converter<Jwt, Mono<AbstractAuthenticationToken>> converter,
            @Value("${assets.security.issuer:}") String issuer,
            @Value("${assets.security.audience:}") String audience,
            @Value("${assets.security.jwk-set-uri:}") String jwks) {
        ReactiveJwtDecoder decoder;
        if (issuer.isBlank() || audience.isBlank() || jwks.isBlank())
            decoder = token -> Mono.error(new JwtException("Asset price trust is not configured"));
        else {
            var configured = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
            configured.setJwtValidator(
                    AccessTokenValidators.forIssuerAndAudience(issuer, audience));
            decoder = configured;
        }
        var paths = new String[] {"/api/v1/asset-prices", "/api/v1/asset-prices/**"};
        var matcher =
                new OrServerWebExchangeMatcher(
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, paths),
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.PUT, paths),
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.PATCH, paths),
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.DELETE, paths));
        return http.securityMatcher(matcher)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(
                        exchanges ->
                                exchanges
                                        .anyExchange()
                                        .access(
                                                (authentication, context) ->
                                                        authentication
                                                                .filter(a -> a.isAuthenticated())
                                                                .map(
                                                                        a -> {
                                                                            try {
                                                                                AssetPriceActorRules
                                                                                        .require(
                                                                                                PrincipalSupport
                                                                                                        .extract(
                                                                                                                a),
                                                                                                issuer,
                                                                                                Clock
                                                                                                        .systemUTC());
                                                                                return (AuthorizationResult)
                                                                                        new AuthorizationDecision(
                                                                                                true);
                                                                            } catch (
                                                                                    ResponseStatusException
                                                                                            denied) {
                                                                                return (AuthorizationResult)
                                                                                        new AuthorizationDecision(
                                                                                                false);
                                                                            }
                                                                        })
                                                                .defaultIfEmpty(
                                                                        new AuthorizationDecision(
                                                                                false))))
                .oauth2ResourceServer(
                        resource ->
                                resource.jwt(
                                        jwt ->
                                                jwt.jwtDecoder(decoder)
                                                        .jwtAuthenticationConverter(converter)))
                .build();
    }
}
