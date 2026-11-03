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


package io.fireflysoftware.core.banking.accounts.core.services.status;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.status.AccountStatusHistoryDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface AccountStatusHistoryService {

    /**
     * Retrieve a paginated list of status history entries for a specific account.
     */
    Mono<PaginationResponse<AccountStatusHistoryDTO>> listStatusHistory(UUID accountId, PaginationRequest paginationRequest);

    /**
     * Create a new status history record for a specific account.
     */
    Mono<AccountStatusHistoryDTO> createStatusHistory(UUID accountId, AccountStatusHistoryDTO historyDTO);

    /**
     * Retrieve a specific status history record by ID, ensuring it belongs to the specified account.
     */
    Mono<AccountStatusHistoryDTO> getStatusHistory(UUID accountId, UUID historyId);

    /**
     * Update an existing status history record associated with a specific account.
     */
    Mono<AccountStatusHistoryDTO> updateStatusHistory(UUID accountId, UUID historyId, AccountStatusHistoryDTO historyDTO);

    /**
     * Delete a specific status history record by its unique ID, ensuring it belongs to the specified account.
     */
    Mono<Void> deleteStatusHistory(UUID accountId, UUID historyId);
}
