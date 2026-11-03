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

import io.fireflysoftware.core.banking.accounts.core.mappers.crypto.AssetPriceMapper;
import io.fireflysoftware.core.banking.accounts.core.services.crypto.AssetPriceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.models.repositories.crypto.AssetPriceRepository;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implementation of the AssetPriceService interface. Provides business logic for managing asset
 * prices.
 */
@Service
@Transactional
public class AssetPriceServiceImpl implements AssetPriceService {

    @Autowired private AssetPriceRepository repository;

    @Autowired private AssetPriceMapper mapper;

    @Override
    public Mono<PaginationResponse<AssetPriceDTO>> getAllAssetPrices(
            PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findAllAssetPrices(pageable),
                repository::countAllAssetPrices);
    }

    @Override
    public Mono<AssetPriceDTO> getAssetPrice(UUID assetPriceId) {
        return repository.findById(assetPriceId).map(mapper::toDTO);
    }

    @Override
    public Mono<AssetPriceDTO> createAssetPrice(AssetPriceDTO assetPriceDTO) {
        return Mono.defer(
                () -> {
                    if (assetPriceDTO == null || assetPriceDTO.getAssetPriceId() != null)
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Server assigns asset price identity"));
                    return repository.save(mapper.toEntity(assetPriceDTO)).map(mapper::toDTO);
                });
    }

    @Override
    public Mono<AssetPriceDTO> updateAssetPrice(UUID assetPriceId, AssetPriceDTO assetPriceDTO) {
        return Mono.defer(
                () -> {
                    if (assetPriceDTO == null)
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Asset price body required"));
                    return repository
                            .findById(assetPriceId)
                            .flatMap(
                                    existing -> {
                                        existing.setAssetSymbol(assetPriceDTO.getAssetSymbol());
                                        existing.setQuoteCurrency(assetPriceDTO.getQuoteCurrency());
                                        existing.setPrice(assetPriceDTO.getPrice());
                                        existing.setAsOfDatetime(assetPriceDTO.getAsOfDatetime());
                                        existing.setPriceSource(assetPriceDTO.getPriceSource());
                                        return repository.save(existing);
                                    })
                            .map(mapper::toDTO);
                });
    }

    @Override
    public Mono<Void> deleteAssetPrice(UUID assetPriceId) {
        return repository.findById(assetPriceId).flatMap(repository::delete);
    }

    @Override
    public Mono<PaginationResponse<AssetPriceDTO>> getAssetPricesBySymbol(
            String assetSymbol, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable -> repository.findByAssetSymbol(assetSymbol, pageable),
                () -> repository.countByAssetSymbol(assetSymbol));
    }

    @Override
    public Mono<PaginationResponse<AssetPriceDTO>> getAssetPricesBySymbolAndCurrency(
            String assetSymbol, String quoteCurrency, PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable ->
                        repository.findByAssetSymbolAndQuoteCurrency(
                                assetSymbol, quoteCurrency, pageable),
                () -> repository.countByAssetSymbolAndQuoteCurrency(assetSymbol, quoteCurrency));
    }

    @Override
    public Mono<AssetPriceDTO> getLatestAssetPrice(String assetSymbol, String quoteCurrency) {
        return repository.findLatestPrice(assetSymbol, quoteCurrency).map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<AssetPriceDTO>> getHistoricalAssetPrices(
            String assetSymbol,
            LocalDateTime startDate,
            LocalDateTime endDate,
            PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable ->
                        repository.findByAssetSymbolAndAsOfDatetimeBetween(
                                assetSymbol, startDate, endDate, pageable),
                () ->
                        repository.countByAssetSymbolAndAsOfDatetimeBetween(
                                assetSymbol, startDate, endDate));
    }

    @Override
    public Mono<PaginationResponse<AssetPriceDTO>> getHistoricalAssetPricesByCurrency(
            String assetSymbol,
            String quoteCurrency,
            LocalDateTime startDate,
            LocalDateTime endDate,
            PaginationRequest paginationRequest) {
        return PaginationUtils.paginateQuery(
                paginationRequest,
                mapper::toDTO,
                pageable ->
                        repository.findByAssetSymbolAndQuoteCurrencyAndAsOfDatetimeBetween(
                                assetSymbol, quoteCurrency, startDate, endDate, pageable),
                () ->
                        repository.countByAssetSymbolAndQuoteCurrencyAndAsOfDatetimeBetween(
                                assetSymbol, quoteCurrency, startDate, endDate));
    }

    @Override
    public Mono<Void> deleteAssetPricesBySymbol(String assetSymbol) {
        return repository.deleteByAssetSymbol(assetSymbol);
    }

    @Override
    public Mono<Void> deleteAssetPricesBySymbolAndCurrency(
            String assetSymbol, String quoteCurrency) {
        return repository.deleteByAssetSymbolAndQuoteCurrency(assetSymbol, quoteCurrency);
    }

    @Override
    public Mono<Void> deleteAssetPricesOlderThan(LocalDateTime date) {
        return repository.deleteByAsOfDatetimeBefore(date);
    }
}
