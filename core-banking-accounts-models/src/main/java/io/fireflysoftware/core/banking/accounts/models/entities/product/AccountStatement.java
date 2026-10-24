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


package io.fireflysoftware.core.banking.accounts.models.entities.product;

import io.fireflysoftware.core.banking.accounts.models.entities.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Table("account_statement")
public class AccountStatement extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column("account_statement_id")
    private UUID accountStatementId;

    @Column("account_id")
    private UUID accountId;

    @Column("currency")
    private String currency;

    @Column("opening_balance")
    private BigDecimal openingBalance;

    @Column("credits")
    private BigDecimal credits;

    @Column("debits")
    private BigDecimal debits;

    @Column("closing_balance")
    private BigDecimal closingBalance;

    @Transient
    private boolean newRow = true;

    @Override
    public UUID getId() {
        return accountStatementId;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }
}
