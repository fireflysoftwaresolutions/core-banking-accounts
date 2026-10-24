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


package io.fireflysoftware.core.banking.accounts.core.mappers.crypto;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.crypto.AssetPriceDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.crypto.AssetPrice;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between AssetPrice entities and AssetPriceDTO objects.
 */
@Component
public class AssetPriceMapper {

    /**
     * Converts an AssetPrice entity to an AssetPriceDTO.
     *
     * @param assetPrice the entity to convert
     * @return the converted DTO
     */
    public AssetPriceDTO toDTO(AssetPrice assetPrice) {
        if (assetPrice == null) {
            return null;
        }

        return AssetPriceDTO.builder()
                .assetPriceId(assetPrice.getAssetPriceId())
                .assetSymbol(assetPrice.getAssetSymbol())
                .quoteCurrency(assetPrice.getQuoteCurrency())
                .price(assetPrice.getPrice())
                .asOfDatetime(assetPrice.getAsOfDatetime())
                .priceSource(assetPrice.getPriceSource())
                .dateCreated(assetPrice.getDateCreated())
                .dateUpdated(assetPrice.getDateUpdated())
                .build();
    }

    /**
     * Converts an AssetPriceDTO to an AssetPrice entity.
     *
     * @param assetPriceDTO the DTO to convert
     * @return the converted entity
     */
    public AssetPrice toEntity(AssetPriceDTO assetPriceDTO) {
        if (assetPriceDTO == null) {
            return null;
        }

        AssetPrice assetPrice = new AssetPrice();
        assetPrice.setAssetPriceId(assetPriceDTO.getAssetPriceId());
        assetPrice.setAssetSymbol(assetPriceDTO.getAssetSymbol());
        assetPrice.setQuoteCurrency(assetPriceDTO.getQuoteCurrency());
        assetPrice.setPrice(assetPriceDTO.getPrice());
        assetPrice.setAsOfDatetime(assetPriceDTO.getAsOfDatetime());
        assetPrice.setPriceSource(assetPriceDTO.getPriceSource());
        
        return assetPrice;
    }
}