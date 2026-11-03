/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.banking.accounts.web.card;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import io.fireflysoftware.core.banking.accounts.sdk.invoker.ApiClient;
import io.fireflysoftware.core.banking.accounts.sdk.invoker.auth.HttpBearerAuth;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

/** Actual packaged owner, complete generated SDK, native receiver and original audit. The identity/PDP server is controlled. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountCardFenceRuntimeIT {
    final ObjectMapper json=new ObjectMapper();
    final UUID tenant=UUID.randomUUID(),actor=UUID.randomUUID(),party=UUID.randomUUID();
    final String runtimePassword=UUID.randomUUID().toString()+UUID.randomUUID(),writerPassword=UUID.randomUUID().toString()+UUID.randomUUID(),readerPassword=UUID.randomUUID().toString()+UUID.randomUUID(),cachePassword=UUID.randomUUID().toString()+UUID.randomUUID();
    final Set<UUID> accounts=ConcurrentHashMap.newKeySet();
    final AtomicBoolean allowOwner=new AtomicBoolean(true);
    final AtomicInteger businessEvaluations=new AtomicInteger();
    final List<Process> processes=new ArrayList<>();
    final List<Path> logs=new ArrayList<>();
    final List<Map<String,Object>> protocol=Collections.synchronizedList(new ArrayList<>());
    String database,user,password,cacheDatabase;int databasePort,httpPort;Path source,mainJar,output;
    RSAKey key;HttpServer authority;Process main;
    String jdbc(String name){return "jdbc:postgresql://127.0.0.1:"+databasePort+"/"+name;}
    Connection owner()throws Exception{return DriverManager.getConnection(jdbc(database),user,password);}
    static void execute(Connection c,String value)throws Exception{try(var s=c.createStatement()){s.execute(value);}}
    static String scalar(Connection c,String value)throws Exception{try(var s=c.createStatement();var r=s.executeQuery(value)){assertTrue(r.next());return r.getString(1);}}
    String issuer(){return authorityBase()+"/issuer";}
    String authorityBase(){return "http://127.0.0.1:"+authority.getAddress().getPort();}
    static int port()throws Exception{try(var s=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){return s.getLocalPort();}}
    void respond(HttpExchange x,int status,Object value)throws java.io.IOException{byte[] bytes=json.writeValueAsBytes(value);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();}
    String token(String purpose,String client,Map<String,Object> changes)throws Exception {
        var claims=new HashMap<String,Object>();claims.put("iss",issuer());claims.put("sub","controlled-account-owner");claims.put("aud",List.of("core-accounts"));claims.put("tenant","opaque-account-tenant");claims.put("azp",client);claims.put("scope","accounts:card-enrollment:"+purpose);claims.put("principal_kind","HUMAN");claims.put("actor_id",actor.toString());claims.put("party_id",party.toString());claims.put("nbf",Date.from(Instant.now().minusSeconds(1)));claims.put("exp",Date.from(Instant.now().plusSeconds(120)));claims.putAll(changes);
        var builder=new JWTClaimsSet.Builder();for(var e:claims.entrySet()){if(e.getKey().equals("exp"))builder.expirationTime((Date)e.getValue());else if(e.getKey().equals("nbf"))builder.notBeforeTime((Date)e.getValue());else builder.claim(e.getKey(),e.getValue());}
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),builder.build());jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    void authority()throws Exception {
        key=new RSAKeyGenerator(2048).keyID("controlled-account-card-fence").generate();authority=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        authority.createContext("/jwks",x->respond(x,200,new JWKSet(key.toPublicJWK()).toJSONObject()));
        authority.createContext("/v1/data/account_fence/",x->{try {
            var in=json.readTree(x.getRequestBody()).path("input");var subject=in.path("subject");String action=in.path("action").asText(),resource=in.path("resource").asText();
            boolean evaluation=x.getRequestURI().getPath().endsWith("evaluation");
            boolean allowed="controlled-account-owner".equals(subject.path("subject").asText())&&issuer().equals(subject.path("issuer").asText())&&"opaque-account-tenant".equals(subject.path("tenantId").asText());
            if(evaluation){
                businessEvaluations.incrementAndGet();String purpose=in.path("context").path("purpose").asText();
                allowed=allowed&&allowOwner.get()&&Set.of("ACQUIRE","CLAIM","CANCEL","READ").contains(purpose)&&action.equals("accounts.card-enrollment."+purpose.toLowerCase(Locale.ROOT))
                    &&tenant.toString().equals(in.path("context").path("tenantId").asText())&&"opaque-account-tenant".equals(in.path("context").path("platformTenantId").asText())
                    &&accounts.stream().anyMatch(a->resource.equals("accounts/account/"+a)&&a.toString().equals(in.path("context").path("accountId").asText()))
                    &&subject.path("scopes").toString().contains("\"accounts:card-enrollment:"+purpose.toLowerCase(Locale.ROOT)+"\"");
            }else allowed=allowed&&Set.of("POST","GET").contains(action)&&accounts.stream().anyMatch(a->resource.startsWith("/api/v1/accounts/"+a+"/card-enrollments"));
            respond(x,200,evaluation?Map.of("result",Map.of("allow",allowed,"policy_revision","controlled-account-fence-r1"),"decision_id",UUID.randomUUID().toString()):Map.of("result",allowed));
        }catch(Exception failure){respond(x,500,Map.of("error","controlled_policy_failure"));}});
        authority.start();
    }
    @BeforeAll void start()throws Exception {
        Path profile=Path.of(Objects.requireNonNull(System.getProperty("accounts.card-fence.http.database-profile")));
        assertEquals(Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE),Files.getPosixFilePermissions(profile));
        var p=json.readTree(Files.readAllBytes(profile));assertEquals("isolated-owned-account-card-fence-http",p.path("purpose").asText());assertEquals("127.0.0.1",p.path("host").asText());
        database=p.path("database").asText();assertTrue(database.matches("accounts_card_fence_http_[a-z0-9_]+"));databasePort=p.path("port").asInt();user=p.path("user").asText();password=p.path("password").asText();
        source=Path.of(System.getProperty("accounts.card-fence.http.source")).toAbsolutePath();mainJar=Path.of(System.getProperty("accounts.card-fence.http.main")).toAbsolutePath();assertTrue(Files.isRegularFile(mainJar));
        output=Path.of(System.getProperty("accounts.card-fence.http.output")).toAbsolutePath();Files.createDirectories(output);cacheDatabase="account_fence_cache_"+UUID.randomUUID().toString().replace("-","");
        try(var c=owner()) {
            assertEquals("0",scalar(c,"SELECT count(*) FROM pg_tables WHERE schemaname='public'"));
            execute(c,"REVOKE CREATE ON SCHEMA public FROM PUBLIC;CREATE ROLE firefly_funds_policy_reader NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;CREATE ROLE firefly_funds_policy_writer NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS");
            try(var files=Files.list(source.resolve("core-banking-accounts-models/src/main/resources/db/migration"))){for(Path f:files.filter(v->v.getFileName().toString().matches("V[0-9]+__.*\\.sql")).sorted(Comparator.comparingInt(v->Integer.parseInt(v.getFileName().toString().split("__")[0].substring(1)))).toList())execute(c,Files.readString(f));}
            execute(c,"CREATE ROLE firefly_audit_guard NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS;CREATE ROLE firefly_audit_writer LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS PASSWORD '"+writerPassword+"';CREATE ROLE firefly_audit_reader LOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS PASSWORD '"+UUID.randomUUID()+"'");
            for(String resource:List.of("db/optional/immutable-audit/postgresql.sql","db/optional/immutable-audit/completed-append.sql")){try(var stream=getClass().getClassLoader().getResourceAsStream(resource)){assertNotNull(stream);execute(c,new String(stream.readAllBytes(),StandardCharsets.UTF_8));}}
            execute(c,"SET ROLE firefly_audit_guard;GRANT EXECUTE ON FUNCTION public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean) TO firefly_audit_writer;RESET ROLE");
            execute(c,Files.readString(source.resolve("core-banking-accounts-models/src/main/resources/db/optional/card-enrollment/V17__Account_Card_Enrollment_Fence.sql")));
            execute(c,"CREATE ROLE account_fence_http_reader LOGIN NOSUPERUSER NOBYPASSRLS INHERIT PASSWORD '"+readerPassword+"' IN ROLE accounts_card_fence_reader;CREATE ROLE account_fence_http_runtime LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT PASSWORD '"+runtimePassword+"';GRANT USAGE ON SCHEMA public TO account_fence_http_runtime;CREATE ROLE account_fence_http_cache LOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT PASSWORD '"+cachePassword+"';REVOKE CONNECT,CREATE,TEMP ON DATABASE "+database+" FROM PUBLIC;GRANT CONNECT ON DATABASE "+database+" TO firefly_audit_writer,account_fence_http_reader,account_fence_http_runtime");
            execute(c,"CREATE DATABASE "+cacheDatabase);
        }
        try(var c=DriverManager.getConnection(jdbc(cacheDatabase),user,password)){
            execute(c,"REVOKE CONNECT,CREATE,TEMP ON DATABASE "+cacheDatabase+" FROM PUBLIC;GRANT CONNECT ON DATABASE "+cacheDatabase+" TO account_fence_http_cache;REVOKE CREATE ON SCHEMA public FROM PUBLIC;CREATE SCHEMA owned_cache;CREATE TABLE owned_cache.cache_entries(cache_name VARCHAR(255) NOT NULL,cache_key VARCHAR(1024) NOT NULL,value BYTEA NOT NULL,expires_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(cache_name,cache_key));GRANT USAGE ON SCHEMA owned_cache TO account_fence_http_cache;GRANT SELECT,INSERT,UPDATE,DELETE ON owned_cache.cache_entries TO account_fence_http_cache");
        }
        assertEquals("42501",assertThrows(SQLException.class,()->DriverManager.getConnection(jdbc(database),"account_fence_http_cache",cachePassword)).getSQLState());
        assertEquals("42501",assertThrows(SQLException.class,()->DriverManager.getConnection(jdbc(cacheDatabase),"firefly_audit_writer",writerPassword)).getSQLState());
        authority();httpPort=port();startMain();
    }
    void startMain()throws Exception {
        String prefix="--firefly.accounts.card-enrollment.";
        var cmd=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx384m","-jar",mainJar.toString(),"--server.address=127.0.0.1","--server.port="+httpPort,"--DB_HOST=127.0.0.1","--DB_PORT="+databasePort,"--DB_NAME="+database,"--DB_USERNAME=account_fence_http_runtime","--DB_SSL_MODE=disable","--spring.flyway.enabled=false","--firefly.eda.enabled=false","--firefly.stepevents.enabled=false","--firefly.security.resource-server.enabled=true","--firefly.security.resource-server.strict-policy=true","--firefly.security.resource-server.issuer="+issuer(),"--firefly.security.resource-server.jwk-set-uri="+authorityBase()+"/jwks","--firefly.security.resource-server.audiences=core-accounts","--firefly.security.resource-server.tenant-claim-paths=tenant","--firefly.security.resource-server.scope-claim-paths=scope","--firefly.security.resource-server.tenant-required=true","--firefly.security.resource-server.permit-matchers=/v3/api-docs,/actuator/health",prefix+"enabled=true",prefix+"opa.enabled=true",prefix+"opa.endpoint="+authorityBase(),prefix+"opa.decision-path=account_fence/allow",prefix+"opa.evaluation-path=account_fence/evaluation",prefix+"opa.allow-loopback-http=true",prefix+"native.issuer="+issuer(),prefix+"native.platform-tenant=opaque-account-tenant",prefix+"native.resource-tenant="+tenant,prefix+"native.audience=core-accounts",prefix+"native.client-claim=azp",prefix+"native.actor-claim=actor_id",prefix+"native.party-claim=party_id",prefix+"native.kind-claim=principal_kind",prefix+"native.acquire-client-ids=customer-channel",prefix+"native.claim-client-ids=core-cards",prefix+"native.cancel-client-ids=customer-channel",prefix+"native.read-client-ids=customer-channel,core-cards"));
        for(String role:List.of("command","reader"))cmd.addAll(List.of(prefix+"database."+role+".host=127.0.0.1",prefix+"database."+role+".port="+databasePort,prefix+"database."+role+".name="+database,prefix+"database."+role+".username="+(role.equals("command")?"firefly_audit_writer":"account_fence_http_reader"),prefix+"database."+role+".password=${ACCOUNT_FENCE_"+role.toUpperCase(Locale.ROOT)+"_PASSWORD}",prefix+"database."+role+".ssl-mode=disable"));
        cmd.addAll(List.of("--firefly.cache.default-cache-type=POSTGRES","--firefly.cache.postgres.enabled=true","--firefly.cache.postgres.host=127.0.0.1","--firefly.cache.postgres.port="+databasePort,"--firefly.cache.postgres.database="+cacheDatabase,"--firefly.cache.postgres.username=account_fence_http_cache","--firefly.cache.postgres.password=${ACCOUNT_FENCE_CACHE_PASSWORD}","--firefly.cache.postgres.ssl-mode=disable","--firefly.cache.postgres.schema=owned_cache","--firefly.cache.postgres.cache-table=cache_entries","--firefly.cache.postgres.auto-create-schema=false","--firefly.cache.postgres.min-pool-size=1","--firefly.cache.postgres.max-pool-size=2","--firefly.cache.postgres.initialization-timeout=15s","--firefly.cache.postgres.shutdown-timeout=5s"));
        Path log=output.resolve("main-"+processes.size()+".log");logs.add(log);var builder=new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DB_PASSWORD",runtimePassword);builder.environment().put("ACCOUNT_FENCE_COMMAND_PASSWORD",writerPassword);builder.environment().put("ACCOUNT_FENCE_READER_PASSWORD",readerPassword);builder.environment().put("ACCOUNT_FENCE_CACHE_PASSWORD",cachePassword);
        main=builder.start();processes.add(main);var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        for(int i=0;i<160;i++){assertTrue(main.isAlive(),"Owned Accounts Main exited before readiness");try{if(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+httpPort+"/v3/api-docs")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return;}catch(java.io.IOException starting){}Thread.sleep(250);}fail("Owned Accounts Main startup timeout");
    }
    void stopMain()throws Exception{if(main!=null){main.destroy();if(!main.waitFor(8,TimeUnit.SECONDS)){main.destroyForcibly();assertTrue(main.waitFor(5,TimeUnit.SECONDS));}assertFalse(main.isAlive());}}
    UUID account()throws Exception {
        UUID id=UUID.randomUUID(),contract=UUID.randomUUID();try(var c=owner()){
            execute(c,"INSERT INTO account(account_id,contract_id,account_number,account_type,currency,open_date,account_status,branch_id) VALUES('"+id+"','"+contract+"','native-fence-"+id+"','CHECKING','USD',CURRENT_DATE,'OPEN','"+UUID.randomUUID()+"')");
            execute(c,"INSERT INTO servicing_mandate(servicing_mandate_id,account_id,party_id,role,active) VALUES('"+UUID.randomUUID()+"','"+id+"','"+party+"','OWNER',true)");
            execute(c,"INSERT INTO account_money_enrollment(account_id,tenant_id,enrollment_id,contract_id,currency,kind,mode,control,version) VALUES('"+id+"','"+tenant+"','"+UUID.randomUUID()+"','"+contract+"','USD','CUSTOMER','MANAGED','ACTIVE',7)");
        }accounts.add(id);return id;
    }
    Map<String,String> snapshot()throws Exception {
        var result=new TreeMap<String,String>();try(var c=owner()){
            var tables=new TreeSet<>(List.of("firefly_audit.audit_head","firefly_audit.audit_event","firefly_audit.audit_outbox"));try(var s=c.createStatement();var rows=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")){while(rows.next())tables.add(rows.getString(1));}
            for(String table:tables){assertTrue(table.matches("[a-z_]+(?:\\.[a-z_]+)?"));result.put(table,scalar(c,"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+table+" t"));}
        }return result;
    }
    static Class<?> model(String name)throws Exception{return Class.forName("io.fireflysoftware.core.banking.accounts.sdk.model.AccountCardEnrollment"+name);}
    Object request(String type,Map<String,Object> values)throws Exception{return ApiClient.createDefaultMapper(null).convertValue(values,model(type));}
    Object invoke(String method,String credential,Object...args)throws Exception{return invokeUsing(new ApiClient(),method,credential,args);}
    Object invokeUsing(ApiClient client,String method,String credential,Object...args)throws Exception {
        client.setBasePath("http://127.0.0.1:"+httpPort);if(credential!=null)((HttpBearerAuth)client.getAuthentication("accountCardEnrollmentBearer")).setBearerToken(credential);
        Class<?> type=Class.forName("io.fireflysoftware.core.banking.accounts.sdk.api.AccountCardEnrollmentApi");Object api=type.getConstructor(ApiClient.class).newInstance(client);
        Method target=Arrays.stream(type.getMethods()).filter(m->m.getName().equals(method)).findFirst().orElseThrow();
        assertEquals(model("Observation"),((ParameterizedType)target.getGenericReturnType()).getActualTypeArguments()[0]);
        try{return ((Mono<?>)target.invoke(api,args)).doOnError(error->{if(error instanceof WebClientResponseException response){String reason="UNCLASSIFIED";try{reason=json.readTree(response.getResponseBodyAsByteArray()).path("reason").asText("UNCLASSIFIED");}catch(Exception ignored){}protocol.add(Map.of("case","sdk-failure","operation",method,"httpStatus",response.getStatusCode().value(),"reason",Set.of("CONFLICT","REFUSED","NOT_FOUND","UNAVAILABLE","UNKNOWN","WITHHELD").contains(reason)?reason:"UNCLASSIFIED"));}}).block(Duration.ofSeconds(15));}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}
    }
    JsonNode tree(Object value){return ApiClient.createDefaultMapper(null).valueToTree(value);}
    Object acquire(UUID account,UUID action,long deadline)throws Exception{return invoke("acquireAccountCardEnrollment",token("acquire","customer-channel",Map.of()),account,request("Acquire",Map.of("actionId",action,"expectedAccountVersion",7,"acceptNotAfterEpochMillis",deadline,"reasonCode","CUSTOMER_REQUEST")));}
    Object read(UUID account,UUID fence,UUID operation)throws Exception{return invoke("recoverAccountCardEnrollment",token("read","customer-channel",Map.of()),account,fence,operation);}
    void refused(int status,org.junit.jupiter.api.function.Executable call){var e=assertThrows(WebClientResponseException.class,call);assertEquals(status,e.getStatusCode().value());}
    @Test @Order(1) void signedOwnerCommitsAuditedFenceAndGeneratedReceiptReadAndReplayLeaveAllRowsUntouched()throws Exception {
        UUID account=account(),action=UUID.randomUUID();long deadline=System.currentTimeMillis()+120000;var acquired=acquire(account,action,deadline);var receipt=tree(acquired);assertEquals("HELD",receipt.path("fence").path("state").asText());assertTrue(receipt.path("currentlyEligible").asBoolean());
        var before=snapshot();var recovered=tree(read(account,action,action));var replay=tree(acquire(account,action,deadline));
        for(var actual:List.of(recovered,replay)){assertEquals(receipt.path("fence"),actual.path("fence"));assertEquals(receipt.path("actionReceipt"),actual.path("actionReceipt"));}assertEquals(before,snapshot());
        for(String field:List.of("ObservedAt","EligibilityValidUntil"))assertInstanceOf(Instant.class,acquired.getClass().getMethod("get"+field).invoke(acquired));
        try(var c=owner()){
            assertEquals("1",scalar(c,"SELECT count(*) FROM account_card_enrollment_audit b JOIN firefly_audit.audit_event e ON e.resource_tenant_id=b.tenant_id AND e.event_id=b.event_id AND e.sequence=b.audit_sequence AND e.record_hash=b.audit_record_hash WHERE b.operation_action_id='"+action+"'"));
            assertEquals("1",scalar(c,"SELECT count(*) FROM firefly_audit_private.context x JOIN account_card_enrollment_audit b ON b.audit_context_nonce=x.nonce WHERE b.operation_action_id='"+action+"' AND x.db_session_user='firefly_audit_writer' AND x.new_event"));
            assertEquals("0",scalar(c,"SELECT count(*) FROM accounts_card_fence_private.context"));
        }
        protocol.add(Map.of("case","actual-audited-acquire-read-replay","account",account,"action",action,"originalReceipt",receipt,"before",before,"after",snapshot()));
    }
    @Test @Order(2) void onlyDesignatedCardsClientCanClaimAndClaimedFenceCannotBeCustomerCancelled()throws Exception {
        UUID account=account(),action=UUID.randomUUID(),claim=UUID.randomUUID();var held=tree(acquire(account,action,System.currentTimeMillis()+120000));
        var request=request("Claim",Map.of("claimActionId",claim,"receiptId",held.path("fence").path("receiptId").asText(),"expectedFenceVersion",0));var before=snapshot();
        refused(403,()->invoke("claimAccountCardEnrollment",token("claim","customer-channel",Map.of()),account,action,request));assertEquals(before,snapshot());
        var claimed=tree(invoke("claimAccountCardEnrollment",token("claim","core-cards",Map.of()),account,action,request));assertEquals("CLAIMED",claimed.path("fence").path("state").asText());before=snapshot();
        assertEquals(claimed.path("actionReceipt"),tree(invoke("claimAccountCardEnrollment",token("claim","core-cards",Map.of()),account,action,request)).path("actionReceipt"));
        refused(409,()->invoke("cancelAccountCardEnrollment",token("cancel","customer-channel",Map.of()),account,action,request("Cancel",Map.of("cancellationActionId",UUID.randomUUID(),"receiptId",held.path("fence").path("receiptId").asText(),"expectedFenceVersion",0,"reasonCode","CUSTOMER_CANCELLED"))));assertEquals(before,snapshot());
    }
    @Test @Order(3) void expiredHeldFenceUsesFreshCancellationAuthorityWithoutRenewingOriginalDeadline()throws Exception {
        UUID account=account(),action=UUID.randomUUID(),cancel=UUID.randomUUID();long deadline=System.currentTimeMillis()+1800;var held=tree(acquire(account,action,deadline));while(System.currentTimeMillis()<=deadline)Thread.sleep(25);
        assertFalse(tree(read(account,action,action)).path("currentlyEligible").asBoolean());
        var request=request("Cancel",Map.of("cancellationActionId",cancel,"receiptId",held.path("fence").path("receiptId").asText(),"expectedFenceVersion",0,"reasonCode","CUSTOMER_CANCELLED"));
        var cancelled=tree(invoke("cancelAccountCardEnrollment",token("cancel","customer-channel",Map.of()),account,action,request));assertEquals("CANCELLED",cancelled.path("fence").path("state").asText());assertEquals(deadline,cancelled.path("fence").path("acceptNotAfterEpochMillis").asLong());var before=snapshot();
        assertEquals(cancelled.path("actionReceipt"),tree(invoke("cancelAccountCardEnrollment",token("cancel","customer-channel",Map.of()),account,action,request)).path("actionReceipt"));assertEquals(before,snapshot());
    }
    @Test @Order(4) void signedWrongOwnerTenantScopeAudienceClientAndCurrentPolicyCannotReadOrMutate()throws Exception {
        UUID account=account(),action=UUID.randomUUID();acquire(account,action,System.currentTimeMillis()+120000);var before=snapshot();
        for(var changed:List.of(Map.<String,Object>of("actor_id",UUID.randomUUID().toString()),Map.<String,Object>of("party_id",UUID.randomUUID().toString()),Map.<String,Object>of("tenant","other-tenant"),Map.<String,Object>of("scope","accounts:card-enrollment:acquire"),Map.<String,Object>of("azp","unauthorized-client"),Map.<String,Object>of("principal_kind","MACHINE")))refused(403,()->invoke("recoverAccountCardEnrollment",token("read","customer-channel",changed),account,action,action));
        refused(401,()->invoke("recoverAccountCardEnrollment",token("read","customer-channel",Map.of("aud",List.of("other-audience"))),account,action,action));
        refused(401,()->invoke("recoverAccountCardEnrollment",null,account,action,action));
        allowOwner.set(false);try{refused(403,()->read(account,action,action));}finally{allowOwner.set(true);}assertEquals(before,snapshot());
    }
    @Test @Order(5) void lostReadResponseAndNewJvmRecoverSameOriginalOperationWithoutAnyPhysicalWrite()throws Exception {
        UUID account=account(),action=UUID.randomUUID();var receipt=tree(acquire(account,action,System.currentTimeMillis()+120000));var before=snapshot();var responses=new AtomicInteger();
        var web=ApiClient.buildWebClientBuilder(ApiClient.createDefaultMapper(null)).filter((request,next)->next.exchange(request).flatMap(response->{assertEquals(200,response.statusCode().value());responses.incrementAndGet();return response.releaseBody().then(Mono.error(new java.io.IOException("Controlled received GET response loss")));})).build();
        assertThrows(Exception.class,()->invokeUsing(new ApiClient(web),"recoverAccountCardEnrollment",token("read","customer-channel",Map.of()),account,action,action));assertEquals(1,responses.get());assertEquals(before,snapshot());
        long oldPid=main.pid();stopMain();startMain();assertNotEquals(oldPid,main.pid());var recovered=tree(read(account,action,action));assertEquals(receipt.path("fence"),recovered.path("fence"));assertEquals(receipt.path("actionReceipt"),recovered.path("actionReceipt"));assertEquals(before,snapshot());
        protocol.add(Map.of("case","lost-get-new-jvm-original-action","account",account,"action",action,"oldPid",oldPid,"newPid",main.pid(),"originalReceipt",receipt,"recoveredReceipt",recovered,"before",before,"after",snapshot()));
    }
    @AfterAll void stop()throws Exception {
        try{stopMain();for(Path log:logs){String text=Files.readString(log);for(String secret:List.of(runtimePassword,writerPassword,readerPassword,cachePassword,password))assertFalse(text.contains(secret));}}
        finally{if(authority!=null)authority.stop(0);if(output!=null){Files.writeString(output.resolve("authority-observations.json"),json.writeValueAsString(Map.of("businessEvaluations",businessEvaluations.get(),"policyBackend","CONTROLLED_HTTP_BACKEND_THROUGH_ORIGINAL_OPA_ADAPTER")));Files.writeString(output.resolve("processes.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(processes.stream().map(p->Map.of("pid",p.pid(),"alive",p.isAlive())).toList()));Files.writeString(output.resolve("protocol.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(protocol));Files.writeString(output.resolve("physical.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot()));}}
    }
}
