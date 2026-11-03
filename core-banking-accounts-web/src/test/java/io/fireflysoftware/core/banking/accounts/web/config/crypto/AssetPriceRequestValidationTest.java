package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.core.services.crypto.AssetPriceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.web.controllers.crypto.AssetPriceController;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.reactive.config.EnableWebFlux;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

class AssetPriceRequestValidationTest {
    AssetPriceService service;
    LocalValidatorFactoryBean validator;
    WebTestClient client;
    AnnotationConfigApplicationContext context;

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Profile("asset-request-validation-fixture")
    static class Web {}

    @BeforeEach
    void controller() {
        service = mock(AssetPriceService.class);
        when(service.createAssetPrice(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(service.updateAssetPrice(any(), any()))
                .thenAnswer(call -> Mono.just(call.getArgument(1)));
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("asset-request-validation-fixture");
        context.registerBean(AssetPriceService.class, () -> service);
        context.register(AssetPriceController.class, Web.class);
        context.refresh();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
        if (context != null) context.close();
    }

    Map<String, Object> valid() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("assetSymbol", "A".repeat(20));
        body.put("quoteCurrency", "USDC");
        body.put("price", new BigDecimal("123456789012345678.123456789012345678"));
        body.put("asOfDatetime", "2026-01-01T00:00:00");
        body.put("priceSource", "verified-source");
        return body;
    }

    @Test
    void invalidCreateAndUpdateBodiesFailBeforeCallingTheService() {
        List<Map.Entry<String, Object>> invalid = new ArrayList<>();
        invalid.add(new AbstractMap.SimpleEntry<>("price", null));
        invalid.add(Map.entry("price", BigDecimal.ZERO));
        invalid.add(Map.entry("price", new BigDecimal("-1")));
        invalid.add(Map.entry("price", new BigDecimal("0.1234567890123456789")));
        invalid.add(Map.entry("price", new BigDecimal("1234567890123456789")));
        invalid.add(new AbstractMap.SimpleEntry<>("assetSymbol", null));
        invalid.add(Map.entry("assetSymbol", ""));
        invalid.add(Map.entry("assetSymbol", "btc"));
        invalid.add(Map.entry("assetSymbol", "A".repeat(21)));
        invalid.add(new AbstractMap.SimpleEntry<>("quoteCurrency", null));
        invalid.add(Map.entry("quoteCurrency", ""));
        invalid.add(Map.entry("quoteCurrency", "usdc"));
        invalid.add(Map.entry("quoteCurrency", "A".repeat(11)));
        invalid.add(new AbstractMap.SimpleEntry<>("priceSource", null));
        invalid.add(Map.entry("priceSource", ""));
        invalid.add(Map.entry("priceSource", "S".repeat(51)));
        invalid.add(new AbstractMap.SimpleEntry<>("asOfDatetime", null));
        invalid.add(Map.entry("asOfDatetime", "2999-01-01T00:00:00"));
        for (var invalidField : invalid) {
            var body = valid();
            body.put(invalidField.getKey(), invalidField.getValue());
            client.post()
                    .uri("/api/v1/asset-prices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
            client.put()
                    .uri("/api/v1/asset-prices/" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
        verifyNoInteractions(service);
    }

    @Test
    void actualBeanValidationAcceptsTheExactDatabasePriceAndPairWidths() throws Exception {
        var resolved =
                io.swagger.v3.core.converter.ModelConverters.getInstance()
                        .resolveAsResolvedSchema(
                                new io.swagger.v3.core.converter.AnnotatedType(
                                        AssetPriceDTO.class));
        var openApi =
                new io.swagger.v3.oas.models.OpenAPI()
                        .components(
                                new io.swagger.v3.oas.models.Components()
                                        .schemas(resolved.referencedSchemas));
        new AssetPriceOpenApiConfiguration().assetPriceNumericSchema().customise(openApi);
        var price =
                (io.swagger.v3.oas.models.media.Schema<?>)
                        openApi.getComponents()
                                .getSchemas()
                                .get("AssetPriceDTO")
                                .getProperties()
                                .get("price");
        assertThat(price.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(price.getExclusiveMinimum()).isTrue();
        assertThat(price.getMaximum())
                .isEqualByComparingTo("999999999999999999.999999999999999999");
        assertThat(price.getMultipleOf()).isEqualByComparingTo("0.000000000000000001");
        var exactMapper =
                io.swagger.v3.core.util.Json.mapper()
                        .copy()
                        .enable(
                                com.fasterxml.jackson.databind.DeserializationFeature
                                        .USE_BIG_DECIMAL_FOR_FLOATS);
        var emitted = exactMapper.readTree(exactMapper.writeValueAsString(price));
        assertThat(emitted.get("maximum").decimalValue())
                .isEqualByComparingTo("999999999999999999.999999999999999999");
        assertThat(emitted.get("multipleOf").decimalValue())
                .isEqualByComparingTo("0.000000000000000001");
        var dto = new AssetPriceDTO();
        dto.setAssetSymbol("A".repeat(20));
        dto.setQuoteCurrency("USDC");
        dto.setPrice(new BigDecimal("123456789012345678.123456789012345678"));
        dto.setAsOfDatetime(LocalDateTime.of(2026, 1, 1, 0, 0));
        dto.setPriceSource("S".repeat(50));
        assertThat(validator.validate(dto)).isEmpty();
        dto.setQuoteCurrency("A".repeat(10));
        assertThat(validator.validate(dto)).isEmpty();
        dto.setPrice(new BigDecimal("0.000000000000000001"));
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void exactEighteenDecimalUsdcPairReachesBothControllerOperationsUnrounded() {
        client.post()
                .uri("/api/v1/asset-prices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(valid())
                .exchange()
                .expectStatus()
                .isCreated();
        var id = UUID.randomUUID();
        client.put()
                .uri("/api/v1/asset-prices/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(valid())
                .exchange()
                .expectStatus()
                .isOk();
        var dto = org.mockito.ArgumentCaptor.forClass(AssetPriceDTO.class);
        verify(service).createAssetPrice(dto.capture());
        assertThat(dto.getValue().getPrice()).isEqualTo(valid().get("price"));
        assertThat(dto.getValue().getQuoteCurrency()).isEqualTo("USDC");
        verify(service).updateAssetPrice(eq(id), dto.capture());
        assertThat(dto.getValue().getPrice()).isEqualTo(valid().get("price"));
    }
}
