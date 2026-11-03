/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.banking.accounts.core.services.crypto.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.accounts.core.mappers.crypto.AssetPriceMapper;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;
import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@ExtendWith(MockitoExtension.class)
class AssetPriceServiceImplTest {

    @Mock private AssetPriceRepository repository;

    @Mock private AssetPriceMapper mapper;

    @InjectMocks private AssetPriceServiceImpl assetPriceService;

    private AssetPrice testAssetPrice;
    private AssetPriceDTO testAssetPriceDTO;
    private final UUID TEST_ASSET_PRICE_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private final String TEST_ASSET_SYMBOL = "BTC";
    private final String TEST_QUOTE_CURRENCY = "USD";

    @BeforeEach
    void setUp() {
        // Setup test asset price entity
        testAssetPrice = new AssetPrice();
        testAssetPrice.setAssetPriceId(TEST_ASSET_PRICE_ID);
        testAssetPrice.setAssetSymbol(TEST_ASSET_SYMBOL);
        testAssetPrice.setQuoteCurrency(TEST_QUOTE_CURRENCY);
        testAssetPrice.setPrice(new BigDecimal("45000.00"));
        testAssetPrice.setAsOfDatetime(LocalDateTime.now());
        testAssetPrice.setPriceSource("Coinbase");

        // Setup test asset price DTO
        testAssetPriceDTO =
                AssetPriceDTO.builder()
                        .assetPriceId(TEST_ASSET_PRICE_ID)
                        .assetSymbol(TEST_ASSET_SYMBOL)
                        .quoteCurrency(TEST_QUOTE_CURRENCY)
                        .price(new BigDecimal("45000.00"))
                        .asOfDatetime(LocalDateTime.now())
                        .priceSource("Coinbase")
                        .build();
    }

    @Test
    void getAllAssetPrices_ShouldReturnPaginatedAssetPrices() {
        // Arrange
        PaginationRequest paginationRequest = new PaginationRequest();

        // Create a mock PaginationResponse
        @SuppressWarnings("unchecked")
        PaginationResponse<AssetPriceDTO> mockResponse = mock(PaginationResponse.class);

        // Mock the static PaginationUtils.paginateQuery method
        try (MockedStatic<PaginationUtils> mockedPaginationUtils =
                mockStatic(PaginationUtils.class)) {
            mockedPaginationUtils
                    .when(
                            () ->
                                    PaginationUtils.paginateQuery(
                                            any(PaginationRequest.class),
                                            any(Function.class),
                                            any(Function.class),
                                            any(Supplier.class)))
                    .thenReturn(Mono.just(mockResponse));

            // Act & Assert
            StepVerifier.create(assetPriceService.getAllAssetPrices(paginationRequest))
                    .expectNext(mockResponse)
                    .verifyComplete();

            // Verify the PaginationUtils.paginateQuery was called
            mockedPaginationUtils.verify(
                    () ->
                            PaginationUtils.paginateQuery(
                                    eq(paginationRequest),
                                    any(Function.class),
                                    any(Function.class),
                                    any(Supplier.class)));
        }
    }

    @Test
    void createAssetPrice_ShouldReturnCreatedAssetPrice() {
        // Arrange
        testAssetPriceDTO.setAssetPriceId(null);
        when(mapper.toEntity(any(AssetPriceDTO.class))).thenReturn(testAssetPrice);
        when(repository.save(any(AssetPrice.class))).thenReturn(Mono.just(testAssetPrice));
        when(mapper.toDTO(any(AssetPrice.class))).thenReturn(testAssetPriceDTO);

        // Act & Assert
        StepVerifier.create(assetPriceService.createAssetPrice(testAssetPriceDTO))
                .expectNext(testAssetPriceDTO)
                .verifyComplete();

        verify(mapper).toEntity(testAssetPriceDTO);
        verify(repository).save(testAssetPrice);
        verify(mapper).toDTO(testAssetPrice);
    }

    @Test
    void getAssetPrice_ShouldReturnAssetPrice_WhenAssetPriceExists() {
        // Arrange
        when(repository.findById(TEST_ASSET_PRICE_ID)).thenReturn(Mono.just(testAssetPrice));
        when(mapper.toDTO(testAssetPrice)).thenReturn(testAssetPriceDTO);

        // Act & Assert
        StepVerifier.create(assetPriceService.getAssetPrice(TEST_ASSET_PRICE_ID))
                .expectNext(testAssetPriceDTO)
                .verifyComplete();

        verify(repository).findById(TEST_ASSET_PRICE_ID);
        verify(mapper).toDTO(testAssetPrice);
    }

    @Test
    void getAssetPrice_ShouldReturnEmptyMono_WhenAssetPriceDoesNotExist() {
        // Arrange
        when(repository.findById(TEST_ASSET_PRICE_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(assetPriceService.getAssetPrice(TEST_ASSET_PRICE_ID)).verifyComplete();

        verify(repository).findById(TEST_ASSET_PRICE_ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void updateAssetPrice_ShouldReturnUpdatedAssetPrice_WhenAssetPriceExists() {
        // Arrange
        when(repository.findById(TEST_ASSET_PRICE_ID)).thenReturn(Mono.just(testAssetPrice));
        when(repository.save(testAssetPrice)).thenReturn(Mono.just(testAssetPrice));
        when(mapper.toDTO(testAssetPrice)).thenReturn(testAssetPriceDTO);

        // Act & Assert
        StepVerifier.create(
                        assetPriceService.updateAssetPrice(TEST_ASSET_PRICE_ID, testAssetPriceDTO))
                .expectNext(testAssetPriceDTO)
                .verifyComplete();

        verify(repository).findById(TEST_ASSET_PRICE_ID);
        verify(repository).save(testAssetPrice);
        verify(mapper).toDTO(testAssetPrice);
    }

    @Test
    void updateAssetPrice_ShouldReturnEmptyMono_WhenAssetPriceDoesNotExist() {
        // Arrange
        when(repository.findById(TEST_ASSET_PRICE_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(
                        assetPriceService.updateAssetPrice(TEST_ASSET_PRICE_ID, testAssetPriceDTO))
                .verifyComplete();

        verify(repository).findById(TEST_ASSET_PRICE_ID);
        verifyNoMoreInteractions(mapper, repository);
    }

    @Test
    void deleteAssetPrice_ShouldDeleteAssetPrice_WhenAssetPriceExists() {
        // Arrange
        when(repository.findById(TEST_ASSET_PRICE_ID)).thenReturn(Mono.just(testAssetPrice));
        when(repository.delete(testAssetPrice)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(assetPriceService.deleteAssetPrice(TEST_ASSET_PRICE_ID))
                .verifyComplete();

        verify(repository).findById(TEST_ASSET_PRICE_ID);
        verify(repository).delete(testAssetPrice);
    }

    @Test
    void deleteAssetPrice_ShouldReturnEmptyMono_WhenAssetPriceDoesNotExist() {
        // Arrange
        when(repository.findById(TEST_ASSET_PRICE_ID)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(assetPriceService.deleteAssetPrice(TEST_ASSET_PRICE_ID))
                .verifyComplete();

        verify(repository).findById(TEST_ASSET_PRICE_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void getLatestAssetPrice_ShouldReturnLatestPrice() {
        // Arrange
        when(repository.findLatestPrice(TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY))
                .thenReturn(Mono.just(testAssetPrice));
        when(mapper.toDTO(testAssetPrice)).thenReturn(testAssetPriceDTO);

        // Act & Assert
        StepVerifier.create(
                        assetPriceService.getLatestAssetPrice(
                                TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY))
                .expectNext(testAssetPriceDTO)
                .verifyComplete();

        verify(repository).findLatestPrice(TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY);
        verify(mapper).toDTO(testAssetPrice);
    }

    @Test
    void getLatestAssetPrice_ShouldReturnEmptyMono_WhenNoLatestPriceExists() {
        // Arrange
        when(repository.findLatestPrice(TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(
                        assetPriceService.getLatestAssetPrice(
                                TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY))
                .verifyComplete();

        verify(repository).findLatestPrice(TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void deleteAssetPricesBySymbol_ShouldCallRepository() {
        // Arrange
        when(repository.deleteByAssetSymbol(TEST_ASSET_SYMBOL)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(assetPriceService.deleteAssetPricesBySymbol(TEST_ASSET_SYMBOL))
                .verifyComplete();

        verify(repository).deleteByAssetSymbol(TEST_ASSET_SYMBOL);
    }

    @Test
    void deleteAssetPricesBySymbolAndCurrency_ShouldCallRepository() {
        // Arrange
        when(repository.deleteByAssetSymbolAndQuoteCurrency(TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(
                        assetPriceService.deleteAssetPricesBySymbolAndCurrency(
                                TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY))
                .verifyComplete();

        verify(repository)
                .deleteByAssetSymbolAndQuoteCurrency(TEST_ASSET_SYMBOL, TEST_QUOTE_CURRENCY);
    }

    @Test
    void deleteAssetPricesOlderThan_ShouldCallRepository() {
        // Arrange
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        when(repository.deleteByAsOfDatetimeBefore(cutoffDate)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(assetPriceService.deleteAssetPricesOlderThan(cutoffDate))
                .verifyComplete();

        verify(repository).deleteByAsOfDatetimeBefore(cutoffDate);
    }
}
