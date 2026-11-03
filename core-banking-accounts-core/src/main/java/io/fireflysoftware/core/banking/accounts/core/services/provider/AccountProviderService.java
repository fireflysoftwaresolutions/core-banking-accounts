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


package io.fireflysoftware.core.banking.accounts.core.services.provider;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.provider.AccountProviderDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountProviderService {

    /**
     * Retrieve a paginated list of providers for a specific account.
     */
    Mono<PaginationResponse<AccountProviderDTO>> listProviders(UUID accountId, PaginationRequest paginationRequest);

    /**
     * Create a new provider record for a specific account.
     */
    Mono<AccountProviderDTO> createProvider(UUID accountId, AccountProviderDTO providerDTO);

    /**
     * Retrieve a specific provider by its ID, ensuring it belongs to the specified account.
     */
    Mono<AccountProviderDTO> getProvider(UUID accountId, UUID providerId);

    /**
     * Update an existing provider associated with a specific account.
     */
    Mono<AccountProviderDTO> updateProvider(UUID accountId, UUID providerId, AccountProviderDTO providerDTO);

    /**
     * Delete a specific account provider by its unique ID, ensuring it belongs to the specified account.
     */
    Mono<Void> deleteProvider(UUID accountId, UUID providerId);

    /**
     * Retrieve a paginated list of providers for a specific account space.
     */
    Mono<PaginationResponse<AccountProviderDTO>> listProvidersForSpace(UUID accountId, UUID accountSpaceId, PaginationRequest paginationRequest);

    /**
     * Create a new provider record for a specific account space.
     */
    Mono<AccountProviderDTO> createProviderForSpace(UUID accountId, UUID accountSpaceId, AccountProviderDTO providerDTO);

    /**
     * Retrieve a specific provider by its ID, ensuring it belongs to the specified account space.
     */
    Mono<AccountProviderDTO> getProviderForSpace(UUID accountId, UUID accountSpaceId, UUID providerId);

    /**
     * Update an existing provider associated with a specific account space.
     */
    Mono<AccountProviderDTO> updateProviderForSpace(UUID accountId, UUID accountSpaceId, UUID providerId, AccountProviderDTO providerDTO);

    /**
     * Delete a specific account provider by its unique ID, ensuring it belongs to the specified account space.
     */
    Mono<Void> deleteProviderForSpace(UUID accountId, UUID accountSpaceId, UUID providerId);
}
