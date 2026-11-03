/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.controllers.card.enrollment;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AccountCardFenceFailureDiagnosticsTest {
    @Test void diagnosticsExcludeMessagesSqlAndSuppressedRequestMaterial() {
        var driver=new io.r2dbc.spi.R2dbcTransientResourceException("private-request-value", "40001", 0, "private SQL body");
        var wrapped=new IllegalStateException("Bearer private-request-value",driver);wrapped.addSuppressed(new RuntimeException("private-suppressed-body"));
        var diagnostics=AccountCardFenceFailureDiagnostics.causes(wrapped);assertEquals(2,diagnostics.size());assertEquals("40001",diagnostics.get(1).sqlState());
        assertFalse(diagnostics.toString().contains("private"));assertFalse(diagnostics.toString().contains("Bearer"));assertTrue(diagnostics.getFirst().ownerLocation().contains(getClass().getName()));
    }
    @Test void depthAndCyclicCauseChainsAreBounded() {
        Throwable value=new RuntimeException("private");for(int i=0;i<100;i++)value=new RuntimeException("private",value);
        assertEquals(8,AccountCardFenceFailureDiagnostics.causes(value).size());
        var cycle=new RuntimeException(){@Override public synchronized Throwable getCause(){return this;}};
        assertEquals(1,AccountCardFenceFailureDiagnostics.causes(cycle).size());
    }
}
