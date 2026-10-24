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


package io.fireflysoftware.core.banking.accounts.core.services.parameter;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.parameter.AccountParameterDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountParameterService {

    /**
     * List all parameters (paginated) for a specific account.
     */
    Mono<PaginationResponse<AccountParameterDTO>> listParameters(UUID accountId, PaginationRequest paginationRequest);

    /**
     * Create a new parameter for a specific account.
     */
    Mono<AccountParameterDTO> createParameter(UUID accountId, AccountParameterDTO parameterDTO);

    /**
     * Retrieve a single parameter by its ID, ensuring it belongs to the specified account.
     */
    Mono<AccountParameterDTO> getParameter(UUID accountId, UUID paramId);

    /**
     * Update an existing parameter associated with the specified account.
     */
    Mono<AccountParameterDTO> updateParameter(UUID accountId, UUID paramId, AccountParameterDTO parameterDTO);

    /**
     * Delete a specific parameter by its ID, ensuring it belongs to the specified account.
     */
    Mono<Void> deleteParameter(UUID accountId, UUID paramId);
}