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


package io.fireflysoftware.core.banking.accounts.core.mappers.space;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.space.SpaceTransactionDTO;
import io.fireflysoftware.core.banking.accounts.models.entities.space.SpaceTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper for converting between SpaceTransaction entity and SpaceTransactionDTO.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SpaceTransactionMapper {
    
    /**
     * Convert entity to DTO
     * @param entity the entity to convert
     * @return the DTO
     */
    SpaceTransactionDTO toDTO(SpaceTransaction entity);
    
    /**
     * Convert DTO to entity
     * @param dto the DTO to convert
     * @return the entity
     */
    SpaceTransaction toEntity(SpaceTransactionDTO dto);
    
    /**
     * Update entity from DTO
     * @param dto the source DTO
     * @param entity the target entity
     */
    void updateEntityFromDTO(SpaceTransactionDTO dto, @MappingTarget SpaceTransaction entity);
}
