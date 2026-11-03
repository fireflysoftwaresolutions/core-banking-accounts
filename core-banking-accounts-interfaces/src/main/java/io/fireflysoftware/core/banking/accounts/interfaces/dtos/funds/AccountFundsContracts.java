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

package io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds;

import java.util.UUID;

public final class AccountFundsContracts {
    private AccountFundsContracts() {}

    public enum Kind {
        CUSTOMER,
        BANK_CONTROL
    }

    public enum Mode {
        ENROLLING,
        MANAGED
    }

    public enum Control {
        ACTIVE,
        BLOCKING,
        BLOCKED,
        ENABLING,
        CLOSING,
        CLOSED
    }

    public record Begin(UUID enrollmentId, Kind kind) {}

    public record Manage(UUID actionId, Control target, Long expectedVersion) {}

    public record Link(UUID enrollmentId) {}

    public record Enrollment(
            UUID accountId,
            UUID enrollmentId,
            UUID contractId,
            String currency,
            Kind kind,
            Mode mode,
            Control control,
            long version) {}
}
