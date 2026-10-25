package io.fireflysoftware.core.banking.accounts.core.services.funds.impl;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import io.fireflysoftware.core.banking.accounts.core.services.funds.LedgerFundsPort;
import io.fireflysoftware.core.banking.accounts.interfaces.dtos.funds.AccountFundsContracts.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class SdkLedgerFundsAdapterTest {
    final ObjectMapper json=new ObjectMapper();
    final Enrollment enrollment=new Enrollment(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"USD",Kind.CUSTOMER,Mode.MANAGED,Control.CLOSING,2);
    final List<Map<String,String>> calls=new ArrayList<>();
    final AtomicBoolean wrongContract=new AtomicBoolean();
    final AtomicBoolean closedPreflight=new AtomicBoolean();
    String rejection;
    HttpServer server;

    SdkLedgerFundsAdapter fixture() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/token",exchange->{
            String form=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertThat(form).contains("grant_type=client_credentials");
            byte[] body="{\"access_token\":\"owned-service-token\",\"expires_in\":60,\"token_type\":\"Bearer\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/api/v1/tenants/owned/funds/accounts/",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer owned-service-token");
            assertThat(exchange.getRequestURI().getQuery()).isNull();
            var input=json.readTree(exchange.getRequestBody());String state=input.path("state").asText();
            calls.add(Map.of("state",state,"actionId",input.path("actionId").asText()));
            String body;
            int status;
            if(state.equals("CLOSED")&&rejection!=null) {status=409;body=json.writeValueAsString(Map.of("message",rejection));}
            else {status=200;body=json.writeValueAsString(Map.of("accountId",enrollment.accountId(),"enrollmentId",enrollment.enrollmentId(),"contractId",wrongContract.get()?UUID.randomUUID():enrollment.contractId(),"currency","USD","kind","CUSTOMER","state",closedPreflight.get()?"CLOSED":state));}
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        return new SdkLedgerFundsAdapter(base,base+"/token","owned-client","owned-secret","funds:control",true);
    }
    @Test void realGeneratedSdkBlocksBeforeClosureAndUsesStableDistinctActionIds() throws Exception {
        try {var adapter=fixture();UUID action=UUID.randomUUID();adapter.apply("owned",enrollment,action,Control.CLOSED).block(Duration.ofSeconds(5));
            assertThat(calls).hasSize(2);assertThat(calls.get(0).get("state")).isEqualTo("BLOCKED");assertThat(calls.get(1).get("state")).isEqualTo("CLOSED");
            assertThat(calls.get(1).get("actionId")).isEqualTo(action.toString());assertThat(calls.get(0).get("actionId")).isNotEqualTo(action.toString());
            String first=calls.get(0).get("actionId");adapter.apply("owned",enrollment,action,Control.CLOSED).block(Duration.ofSeconds(5));assertThat(calls.get(2).get("actionId")).isEqualTo(first);
        } finally {if(server!=null)server.stop(0);}
    }
    @Test void onlyAnExactDefinitiveNonzeroClosureResponseCanClearThePendingControl() throws Exception {
        try {var adapter=fixture();rejection="CLOSURE_REQUIRES_BLOCKED_ZERO_FUNDS";
            assertThatThrownBy(()->adapter.apply("owned",enrollment,UUID.randomUUID(),Control.CLOSED).block(Duration.ofSeconds(5))).isInstanceOf(LedgerFundsPort.ClosureRejected.class);
            rejection="UNRELATED_CONFLICT";
            assertThatThrownBy(()->adapter.apply("owned",enrollment,UUID.randomUUID(),Control.CLOSED).block(Duration.ofSeconds(5))).isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(503));
        } finally {if(server!=null)server.stop(0);}
    }
    @Test void sdkResponseMustMatchCanonicalEnrollmentAndLostClosedReplyCanRecover() throws Exception {
        try {var adapter=fixture();wrongContract.set(true);
            assertThatThrownBy(()->adapter.apply("owned",enrollment,UUID.randomUUID(),Control.BLOCKED).block(Duration.ofSeconds(5))).isInstanceOf(ResponseStatusException.class);
            wrongContract.set(false);closedPreflight.set(true);adapter.apply("owned",enrollment,UUID.randomUUID(),Control.CLOSED).block(Duration.ofSeconds(5));
        } finally {if(server!=null)server.stop(0);}
    }
}
