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

package io.fireflysoftware.core.banking.accounts.core.services.funds;

import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LedgerFundsPort {
    final class ClosureRejected extends RuntimeException {
        public ClosureRejected() {
            super("The ledger rejected closure because booked or held funds remain.");
        }
    }

    Mono<Void> apply(String tenant, Enrollment enrollment, UUID actionId, Control target);
}
