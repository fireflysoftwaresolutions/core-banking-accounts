/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.controllers.card.enrollment;

import java.util.*;

final class AccountCardFenceFailureDiagnostics {
    private AccountCardFenceFailureDiagnostics() {}
    record Cause(String type, String ownerLocation, String sqlState) {}
    static List<Cause> causes(Throwable error) {
        var result=new ArrayList<Cause>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=error;cause!=null&&result.size()<8&&seen.add(cause);cause=cause.getCause()) {
            String state=cause instanceof io.r2dbc.spi.R2dbcException database?database.getSqlState():null;
            if(state==null||!state.matches("[A-Z0-9]{5}"))state="NONE";
            String location=Arrays.stream(cause.getStackTrace()).limit(8)
                    .map(frame->frame.getClassName().substring(0,Math.min(180,frame.getClassName().length()))+":"+frame.getLineNumber())
                    .collect(java.util.stream.Collectors.joining(","));
            result.add(new Cause(cause.getClass().getName(),location,state));
        }
        return List.copyOf(result);
    }
}
