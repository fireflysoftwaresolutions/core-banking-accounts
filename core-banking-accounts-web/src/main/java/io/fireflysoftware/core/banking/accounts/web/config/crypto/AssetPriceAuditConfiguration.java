package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AssetPriceAuditConfiguration {
    @Bean
    public AssetPriceActorCallback assetPriceActorCallback(
            SecurityContextPort contexts, @Value("${assets.security.issuer:}") String issuer) {
        return new AssetPriceActorCallback(contexts, issuer, Clock.systemUTC());
    }
}
