package io.fireflysoftware.core.banking.accounts.web.config.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fireflysoftware.core.banking.accounts.core.mappers.crypto.AssetPriceMapper;
import io.fireflysoftware.core.banking.accounts.core.services.crypto.impl.AssetPriceServiceImpl;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;
import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.util.UUID;

class AssetPriceCreateWireOwnershipTest {
    @Test
    void suppliedJsonIdIsRejectedWhileTheGeneratedSchemaRemainsReadOnly() throws Exception {
        var schema =
                ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(AssetPriceDTO.class));
        assertThat(
                        ((io.swagger.v3.oas.models.media.Schema)
                                        schema.schema.getProperties().get("assetPriceId"))
                                .getReadOnly())
                .isTrue();
        String body =
                "{\"assetPriceId\":\""
                        + UUID.randomUUID()
                        + "\",\"assetSymbol\":\"BTC\",\"quoteCurrency\":\"USD\",\"price\":12.34,\"priceSource\":\"owned-wire\",\"dateCreated\":\"2000-01-01T00:00:00\"}";
        var dto = new ObjectMapper().readValue(body, AssetPriceDTO.class);
        assertThat(dto.getDateCreated()).isNull();
        var repository = mock(AssetPriceRepository.class);
        when(repository.save(any())).thenReturn(Mono.just(new AssetPrice()));
        var service = new AssetPriceServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", new AssetPriceMapper());
        assertThatThrownBy(() -> service.createAssetPrice(dto).block())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        error ->
                                assertThat(
                                                ((ResponseStatusException) error)
                                                        .getStatusCode()
                                                        .value())
                                        .isEqualTo(400));
        verifyNoInteractions(repository);
    }
}
