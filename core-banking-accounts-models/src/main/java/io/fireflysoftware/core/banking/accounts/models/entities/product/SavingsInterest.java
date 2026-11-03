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
@Table("savings_interest")
public class SavingsInterest extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column("savings_interest_id")
    private UUID savingsInterestId;

    @Column("account_id")
    private UUID accountId;

    @Column("currency")
    private String currency;

    @Column("balance")
    private BigDecimal balance;

    @Column("annual_rate_percent")
    private BigDecimal annualRatePercent;

    @Column("day_count")
    private Integer dayCount;

    @Column("interest_amount")
    private BigDecimal interestAmount;

    @Transient
    private boolean newRow = true;

    @Override
    public UUID getId() {
        return savingsInterestId;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }
}
