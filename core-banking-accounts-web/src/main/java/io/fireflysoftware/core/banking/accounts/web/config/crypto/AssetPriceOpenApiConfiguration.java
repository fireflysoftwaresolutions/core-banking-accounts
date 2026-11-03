package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration(proxyBeanMethods = false)
public class AssetPriceOpenApiConfiguration {
    @Bean
    OpenApiCustomizer assetPriceNumericSchema() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null)
                return;
            var asset = openApi.getComponents().getSchemas().get("AssetPriceDTO");
            if (asset == null || asset.getProperties() == null) return;
            var price =
                    (io.swagger.v3.oas.models.media.Schema<?>) asset.getProperties().get("price");
            if (price == null) return;
            // The installed annotation resolver parses maximum through double and rounds this
            // bound.
            price.setMinimum(BigDecimal.ZERO);
            price.setExclusiveMinimum(true);
            price.setMaximum(new BigDecimal("999999999999999999.999999999999999999"));
            price.setMultipleOf(new BigDecimal("0.000000000000000001"));
        };
    }
}
