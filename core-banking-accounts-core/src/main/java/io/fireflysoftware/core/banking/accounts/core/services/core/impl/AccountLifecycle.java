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


package io.fireflysoftware.core.banking.accounts.core.services.core.impl;

import io.fireflysoftware.core.banking.accounts.interfaces.enums.core.AccountStatusEnum;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed moves for an account. A draft is the only status that can be removed.
 */
final class AccountLifecycle {

    private static final Map<AccountStatusEnum, Set<AccountStatusEnum>> ALLOWED = Map.of(
            AccountStatusEnum.DRAFT, EnumSet.of(AccountStatusEnum.OPEN),
            AccountStatusEnum.OPEN, EnumSet.of(
                    AccountStatusEnum.SUSPENDED, AccountStatusEnum.DORMANT, AccountStatusEnum.CLOSED),
            AccountStatusEnum.SUSPENDED, EnumSet.of(AccountStatusEnum.OPEN, AccountStatusEnum.CLOSED),
            AccountStatusEnum.DORMANT, EnumSet.of(AccountStatusEnum.OPEN, AccountStatusEnum.CLOSED)
    );

    private AccountLifecycle() {
    }

    static boolean canTransition(AccountStatusEnum from, AccountStatusEnum to) {
        if (from == to) {
            return true;
        }
        Set<AccountStatusEnum> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }
}
