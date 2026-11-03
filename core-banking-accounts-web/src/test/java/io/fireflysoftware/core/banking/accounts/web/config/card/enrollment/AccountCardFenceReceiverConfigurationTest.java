/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.config.card.enrollment;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.banking.accounts.web.FundsSecurityConfiguration;
import io.fireflysoftware.core.banking.accounts.web.config.crypto.AssetPriceSecurityConfiguration;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.rs.*;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;
import java.time.Duration;

class AccountCardFenceReceiverConfigurationTest {
    final Converter<Jwt, Mono<AbstractAuthenticationToken>> converter=jwt->Mono.empty();
    final ReactiveJwtDecoder decoder=token->Mono.error(new JwtException("Matcher-only test never authenticates"));
    final PolicyDecisionPort policies=(principal,action,resource,context)->Mono.just(Decision.deny("Matcher-only test never grants"));
    boolean matches(SecurityWebFilterChain chain,HttpMethod method,String path){return Boolean.TRUE.equals(chain.matches(MockServerWebExchange.from(MockServerHttpRequest.method(method,path))).block(Duration.ofSeconds(3)));}
    @Test void nativeFenceAndExistingFundsAndAssetPriceChainsRetainDisjointPathsAndOriginalOrdering()throws Exception {
        var properties=new ResourceServerProperties();properties.setStrictPolicy(true);
        var fence=new AccountCardFenceReceiverConfiguration().accountCardFenceNativeReceiver(ServerHttpSecurity.http(),new ResourceServerAutoConfiguration(),properties,decoder,converter,policies);
        var fundsMethod=FundsSecurityConfiguration.class.getDeclaredMethod("managedFundsSecurity",ServerHttpSecurity.class,String.class,String.class,String.class);fundsMethod.setAccessible(true);
        var funds=(SecurityWebFilterChain)fundsMethod.invoke(new FundsSecurityConfiguration(),ServerHttpSecurity.http(),"","","");
        var assetMethod=AssetPriceSecurityConfiguration.class.getDeclaredMethod("assetPriceWriteSecurity",ServerHttpSecurity.class,Converter.class,String.class,String.class,String.class);assetMethod.setAccessible(true);
        var asset=(SecurityWebFilterChain)assetMethod.invoke(new AssetPriceSecurityConfiguration(),ServerHttpSecurity.http(),converter,"","","");
        for(String path:new String[]{"/api/v1/accounts/owner/card-enrollments","/api/v1/accounts/owner/card-enrollments/action/claim","/api/v1/accounts/owner/card-enrollments/action/cancel","/api/v1/accounts/owner/card-enrollments/action/operations/original"}){
            assertTrue(matches(fence,HttpMethod.POST,path));assertTrue(matches(fence,HttpMethod.GET,path));assertFalse(matches(funds,HttpMethod.POST,path));assertFalse(matches(asset,HttpMethod.POST,path));
        }
        assertTrue(matches(funds,HttpMethod.POST,"/api/v1/tenants/tenant/funds/accounts/account/control"));assertFalse(matches(fence,HttpMethod.POST,"/api/v1/tenants/tenant/funds/accounts/account/control"));
        assertTrue(matches(asset,HttpMethod.POST,"/api/v1/asset-prices"));assertFalse(matches(asset,HttpMethod.GET,"/api/v1/asset-prices"));assertFalse(matches(fence,HttpMethod.POST,"/api/v1/asset-prices"));assertFalse(matches(fence,HttpMethod.GET,"/api/v1/accounts/account"));
        var fenceMethod=AccountCardFenceReceiverConfiguration.class.getDeclaredMethod("accountCardFenceNativeReceiver",ServerHttpSecurity.class,ResourceServerAutoConfiguration.class,ResourceServerProperties.class,ReactiveJwtDecoder.class,Converter.class,PolicyDecisionPort.class);
        assertEquals(-101,fenceMethod.getAnnotation(Order.class).value());assertEquals(-100,fundsMethod.getAnnotation(Order.class).value());assertEquals(-90,assetMethod.getAnnotation(Order.class).value());
    }
    @Test void receiverCannotStartWithNonStrictPolicyOrUsePermitAllFallback(){
        var properties=new ResourceServerProperties();properties.setStrictPolicy(false);
        assertThrows(IllegalArgumentException.class,()->new AccountCardFenceReceiverConfiguration().accountCardFenceNativeReceiver(ServerHttpSecurity.http(),new ResourceServerAutoConfiguration(),properties,decoder,converter,policies));
        var fallback=new AccountCardFenceFallbackSecurityConfiguration().accountCardFenceUnconfiguredReceiver(ServerHttpSecurity.http());
        assertTrue(matches(fallback,HttpMethod.POST,"/api/v1/accounts/account/card-enrollments"));assertFalse(matches(fallback,HttpMethod.POST,"/api/v1/asset-prices"));assertFalse(matches(fallback,HttpMethod.POST,"/api/v1/tenants/tenant/funds/accounts/account/control"));
    }
}
