/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.core.services.card.enrollment;

import io.fireflysoftware.core.banking.accounts.models.entities.card.enrollment.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.fireflyframework.kernel.audit.*;

public final class AccountCardFenceAuditEvidence {
    private AccountCardFenceAuditEvidence() {}

    public static AuditEvent event(AccountCardFence fence, AccountCardFenceAction action) {
        return new AuditEvent(action.eventId(), new AuditStream(fence.tenantId(), "core-banking-accounts",
                "account-card-enrollment", fence.actionId()),
                new AuditActor(action.actorIssuer(), action.actorSubject(), action.actorClientId(),
                        action.platformTenantId(), null, null),
                "ACCOUNT_CARD_ENROLLMENT_" + action.operation(), AuditEvent.Outcome.COMPLETED,
                fence.actionId(), action.operationReceiptId(), action.operationActionId(), action.recordedAt(),
                digest(fence, action), action.policyRevision());
    }

    public static String digest(AccountCardFence fence, AccountCardFenceAction action) {
        try {
            var bytes = new ByteArrayOutputStream();var out = new DataOutputStream(bytes);
            text(out, "accounts.card.enrollment.evidence");out.writeInt(1);
            for (UUID id : List.of(fence.tenantId(), fence.actorId(), fence.partyId(), fence.accountId(),
                    fence.contractId(), fence.mandateId(), fence.actionId(), fence.receiptId(),
                    action.operationActionId(), action.operationReceiptId(), action.eventId())) {
                out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());
            }
            out.writeLong(fence.accountVersion());out.writeLong(fence.acceptNotAfterEpochMillis());
            out.writeLong(fence.recordedAt().getEpochSecond());out.writeInt(fence.recordedAt().getNano());
            out.writeLong(action.expectedVersion());out.writeLong(action.resultingVersion());
            out.writeLong(action.recordedAt().getEpochSecond());out.writeInt(action.recordedAt().getNano());
            for (String value : List.of(fence.currency(), fence.reasonCode(), action.operation(), action.reasonCode(),
                    action.resultingState(), action.actorIssuer(), action.actorSubject(), action.actorClientId(),
                    action.platformTenantId(), action.policyRevision())) text(out, value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Original account fence audit evidence unavailable", failure);
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);
    }
}
