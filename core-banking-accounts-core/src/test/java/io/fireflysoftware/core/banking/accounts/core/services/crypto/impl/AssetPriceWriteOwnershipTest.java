package io.fireflysoftware.core.banking.accounts.core.services.crypto.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.core.mappers.crypto.AssetPriceMapper;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;
import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

class AssetPriceWriteOwnershipTest {
    @Test
    void createRejectsAssignedIdBeforeRepositoryAccess() {
        var repository = mock(AssetPriceRepository.class);
        var service = new AssetPriceServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", new AssetPriceMapper());
        when(repository.save(any())).thenReturn(Mono.just(new AssetPrice()));
        var dto = new AssetPriceDTO();
        dto.setAssetPriceId(UUID.randomUUID());
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

    @Test
    void updateUsesLoadedRowAndKeepsOriginalCreationTime() {
        var repository = mock(AssetPriceRepository.class);
        var service = new AssetPriceServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", new AssetPriceMapper());
        var id = UUID.randomUUID();
        var original = new AssetPrice();
        original.setAssetPriceId(id);
        var created = LocalDateTime.of(2020, 1, 1, 0, 0);
        original.setDateCreated(created);
        original.setCreatedBy("first-verified-actor");
        when(repository.findById(id)).thenReturn(Mono.just(original));
        when(repository.save(any()))
                .thenAnswer(
                        call -> {
                            AssetPrice stored = call.getArgument(0);
                            assertThat(stored).isSameAs(original);
                            assertThat(stored.getDateCreated()).isEqualTo(created);
                            assertThat(stored.getCreatedBy()).isEqualTo("first-verified-actor");
                            return Mono.just(stored);
                        });
        var dto = new AssetPriceDTO();
        dto.setAssetSymbol("BTC");
        dto.setPriceSource("updated-owner-source");
        dto.setDateCreated(LocalDateTime.now());
        assertThat(service.updateAssetPrice(id, dto).block().getDateCreated()).isEqualTo(created);
    }
}
