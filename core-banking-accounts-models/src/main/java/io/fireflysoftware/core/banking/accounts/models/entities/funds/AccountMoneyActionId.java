package io.fireflysoftware.core.banking.accounts.models.entities.funds;

import java.util.UUID;

public record AccountMoneyActionId(String tenantId, UUID actionId) {}
