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

package io.fireflysoftware.core.banking.accounts.web.controllers.crypto;

import io.fireflysoftware.core.banking.accounts.core.services.crypto.AssetPriceService;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@io.swagger.v3.oas.annotations.security.SecurityScheme(
        name = "assetPriceBearer",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
@Tag(name = "Asset Prices", description = "APIs for managing crypto asset prices")
@RestController
@RequestMapping("/api/v1/asset-prices")
public class AssetPriceController {

    @Autowired private AssetPriceService service;

    @Operation(
            summary = "List All Asset Prices",
            description = "Retrieve a paginated list of all asset prices.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the asset prices",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                PaginationResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset prices found",
                        content = @Content)
            })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AssetPriceDTO>>> getAllAssetPrices(
            @ParameterObject @ModelAttribute PaginationRequest paginationRequest) {
        return service.getAllAssetPrices(paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get Asset Price by ID",
            description = "Retrieve a specific asset price by its unique identifier.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the asset price",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AssetPriceDTO.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Asset price not found",
                        content = @Content)
            })
    @GetMapping(value = "/{assetPriceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AssetPriceDTO>> getAssetPrice(
            @Parameter(description = "Unique identifier of the asset price", required = true)
                    @PathVariable
                    UUID assetPriceId) {
        return service.getAssetPrice(assetPriceId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create Asset Price", description = "Create a new asset price record.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Asset price created successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AssetPriceDTO.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid asset price data provided",
                        content = @Content)
            })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "assetPriceBearer")
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AssetPriceDTO>> createAssetPrice(
            @Parameter(
                            description = "Data for the new asset price record",
                            required = true,
                            schema = @Schema(implementation = AssetPriceDTO.class))
                    @Valid
                    @RequestBody
                    AssetPriceDTO assetPriceDTO) {
        return service.createAssetPrice(assetPriceDTO)
                .map(createdAssetPrice -> ResponseEntity.status(201).body(createdAssetPrice))
                .defaultIfEmpty(ResponseEntity.badRequest().build());
    }

    @Operation(
            summary = "Update Asset Price",
            description = "Update an existing asset price record.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Asset price updated successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AssetPriceDTO.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Asset price not found",
                        content = @Content)
            })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "assetPriceBearer")
    @PutMapping(
            value = "/{assetPriceId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AssetPriceDTO>> updateAssetPrice(
            @Parameter(
                            description = "Unique identifier of the asset price to update",
                            required = true)
                    @PathVariable
                    UUID assetPriceId,
            @Parameter(
                            description = "Updated data for the asset price record",
                            required = true,
                            schema = @Schema(implementation = AssetPriceDTO.class))
                    @Valid
                    @RequestBody
                    AssetPriceDTO assetPriceDTO) {
        return service.updateAssetPrice(assetPriceId, assetPriceDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Asset Price",
            description = "Remove an existing asset price record by its unique identifier.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Asset price deleted successfully",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "Asset price not found",
                        content = @Content)
            })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "assetPriceBearer")
    @DeleteMapping(value = "/{assetPriceId}")
    public Mono<ResponseEntity<Void>> deleteAssetPrice(
            @Parameter(
                            description = "Unique identifier of the asset price to delete",
                            required = true)
                    @PathVariable
                    UUID assetPriceId) {
        return service.deleteAssetPrice(assetPriceId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Operation(
            summary = "Get Asset Prices by Symbol",
            description = "Retrieve a paginated list of asset prices for a specific asset symbol.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the asset prices",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                PaginationResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset prices found for the specified symbol",
                        content = @Content)
            })
    @GetMapping(value = "/asset/{assetSymbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AssetPriceDTO>>> getAssetPricesBySymbol(
            @Parameter(description = "Symbol of the asset (e.g., 'BTC', 'ETH')", required = true)
                    @PathVariable
                    String assetSymbol,
            @ParameterObject @ModelAttribute PaginationRequest paginationRequest) {
        return service.getAssetPricesBySymbol(assetSymbol, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get Asset Prices by Symbol and Currency",
            description =
                    "Retrieve a paginated list of asset prices for a specific asset symbol and"
                            + " quote currency.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the asset prices",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                PaginationResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset prices found for the specified symbol and currency",
                        content = @Content)
            })
    @GetMapping(
            value = "/asset/{assetSymbol}/currency/{quoteCurrency}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AssetPriceDTO>>>
            getAssetPricesBySymbolAndCurrency(
                    @Parameter(
                                    description = "Symbol of the asset (e.g., 'BTC', 'ETH')",
                                    required = true)
                            @PathVariable
                            String assetSymbol,
                    @Parameter(
                                    description =
                                            "Currency in which the asset is priced (e.g., 'USD',"
                                                    + " 'EUR')",
                                    required = true)
                            @PathVariable
                            String quoteCurrency,
                    @ParameterObject @ModelAttribute PaginationRequest paginationRequest) {
        return service.getAssetPricesBySymbolAndCurrency(
                        assetSymbol, quoteCurrency, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get Latest Asset Price",
            description =
                    "Retrieve the latest price for a specific asset symbol and quote currency.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the latest asset price",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AssetPriceDTO.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset price found for the specified symbol and currency",
                        content = @Content)
            })
    @GetMapping(
            value = "/asset/{assetSymbol}/currency/{quoteCurrency}/latest",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<AssetPriceDTO>> getLatestAssetPrice(
            @Parameter(description = "Symbol of the asset (e.g., 'BTC', 'ETH')", required = true)
                    @PathVariable
                    String assetSymbol,
            @Parameter(
                            description =
                                    "Currency in which the asset is priced (e.g., 'USD', 'EUR')",
                            required = true)
                    @PathVariable
                    String quoteCurrency) {
        return service.getLatestAssetPrice(assetSymbol, quoteCurrency)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get Historical Asset Prices",
            description =
                    "Retrieve a paginated list of historical asset prices for a specific asset"
                            + " symbol within a date range.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the historical asset prices",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                PaginationResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "No historical asset prices found for the specified criteria",
                        content = @Content)
            })
    @GetMapping(value = "/asset/{assetSymbol}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AssetPriceDTO>>> getHistoricalAssetPrices(
            @Parameter(description = "Symbol of the asset (e.g., 'BTC', 'ETH')", required = true)
                    @PathVariable
                    String assetSymbol,
            @Parameter(description = "Start date of the range (ISO format)", required = true)
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startDate,
            @Parameter(description = "End date of the range (ISO format)", required = true)
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endDate,
            @ParameterObject @ModelAttribute PaginationRequest paginationRequest) {
        return service.getHistoricalAssetPrices(assetSymbol, startDate, endDate, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Get Historical Asset Prices by Currency",
            description =
                    "Retrieve a paginated list of historical asset prices for a specific asset"
                            + " symbol and quote currency within a date range.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the historical asset prices",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                PaginationResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "No historical asset prices found for the specified criteria",
                        content = @Content)
            })
    @GetMapping(
            value = "/asset/{assetSymbol}/currency/{quoteCurrency}/history",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<PaginationResponse<AssetPriceDTO>>>
            getHistoricalAssetPricesByCurrency(
                    @Parameter(
                                    description = "Symbol of the asset (e.g., 'BTC', 'ETH')",
                                    required = true)
                            @PathVariable
                            String assetSymbol,
                    @Parameter(
                                    description =
                                            "Currency in which the asset is priced (e.g., 'USD',"
                                                    + " 'EUR')",
                                    required = true)
                            @PathVariable
                            String quoteCurrency,
                    @Parameter(
                                    description = "Start date of the range (ISO format)",
                                    required = true)
                            @RequestParam
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            LocalDateTime startDate,
                    @Parameter(description = "End date of the range (ISO format)", required = true)
                            @RequestParam
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            LocalDateTime endDate,
                    @ParameterObject @ModelAttribute PaginationRequest paginationRequest) {
        return service.getHistoricalAssetPricesByCurrency(
                        assetSymbol, quoteCurrency, startDate, endDate, paginationRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Delete Asset Prices by Symbol",
            description = "Delete all asset prices for a specific asset symbol.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Asset prices deleted successfully",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset prices found for the specified symbol",
                        content = @Content)
            })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "assetPriceBearer")
    @DeleteMapping(value = "/asset/{assetSymbol}")
    public Mono<ResponseEntity<Void>> deleteAssetPricesBySymbol(
            @Parameter(description = "Symbol of the asset (e.g., 'BTC', 'ETH')", required = true)
                    @PathVariable
                    String assetSymbol) {
        return service.deleteAssetPricesBySymbol(assetSymbol)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Operation(
            summary = "Delete Asset Prices by Symbol and Currency",
            description = "Delete all asset prices for a specific asset symbol and quote currency.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Asset prices deleted successfully",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset prices found for the specified symbol and currency",
                        content = @Content)
            })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "assetPriceBearer")
    @DeleteMapping(value = "/asset/{assetSymbol}/currency/{quoteCurrency}")
    public Mono<ResponseEntity<Void>> deleteAssetPricesBySymbolAndCurrency(
            @Parameter(description = "Symbol of the asset (e.g., 'BTC', 'ETH')", required = true)
                    @PathVariable
                    String assetSymbol,
            @Parameter(
                            description =
                                    "Currency in which the asset is priced (e.g., 'USD', 'EUR')",
                            required = true)
                    @PathVariable
                    String quoteCurrency) {
        return service.deleteAssetPricesBySymbolAndCurrency(assetSymbol, quoteCurrency)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Operation(
            summary = "Delete Asset Prices Older Than",
            description = "Delete all asset prices older than a specific date.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "204",
                        description = "Asset prices deleted successfully",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "No asset prices found older than the specified date",
                        content = @Content)
            })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "assetPriceBearer")
    @DeleteMapping(value = "/older-than")
    public Mono<ResponseEntity<Void>> deleteAssetPricesOlderThan(
            @Parameter(description = "Cutoff date (ISO format)", required = true)
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime date) {
        return service.deleteAssetPricesOlderThan(date)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
