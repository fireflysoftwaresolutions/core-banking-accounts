package io.fireflysoftware.core.banking.accounts.models.entities.funds;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("account_money_action")
public record AccountMoneyAction(
        @Id @Embedded(onEmpty = Embedded.OnEmpty.USE_EMPTY) AccountMoneyActionId id,
        UUID accountId,
        String target,
        Long expectedVersion,
        String state,
        String actor,
        OffsetDateTime createdAt) {}
