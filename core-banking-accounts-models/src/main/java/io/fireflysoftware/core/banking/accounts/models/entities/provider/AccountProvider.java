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


package io.fireflysoftware.core.banking.accounts.models.entities.provider;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.provider.ProviderStatusEnum;
import io.fireflysoftware.core.banking.accounts.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

/**
 * Manages the relationship between accounts and external Banking-as-a-Service (BaaS) providers.
 * Tracks the status and external references for accounts linked to third-party providers.
 *
 * Business Rules:
 * - Each account can have multiple providers
 * - External references must be unique per provider
 * - Status changes must be tracked for reconciliation
 */
@Table("account_provider")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountProvider extends BaseEntity {

    /**
     * Unique identifier for the provider record
     * Example: 1000001
     */
    @Id
    private UUID accountProviderId;

    /**
     * Reference to the associated account
     * Example: 100001 (links to Account.accountId)
     */
    private UUID accountId;

    /**
     * Name of the BaaS provider
     * Examples:
     * - "ClearBank"
     * - "Banking Circle"
     * - "Modulr"
     */
    private String providerName;

    /**
     * External reference/ID used by the provider
     * Examples:
     * - "CB-ACC-123456" (ClearBank reference)
     * - "BC-1234567890" (Banking Circle reference)
     * - "MOD-ACC-001" (Modulr reference)
     */
    private String externalReference;

    /**
     * Current status of the provider connection
     * Examples with context:
     * - ACTIVE: Fully operational
     * - INACTIVE: Temporarily disabled
     * - PENDING: Awaiting provider setup
     * - SUSPENDED: Provider issues
     */
    private ProviderStatusEnum status;

    /**
     * Optional reference to a specific account space
     * If null, the provider is associated with the entire account
     * Example: 1000001 (links to AccountSpace.accountSpaceId)
     */
    private UUID accountSpaceId;
}
