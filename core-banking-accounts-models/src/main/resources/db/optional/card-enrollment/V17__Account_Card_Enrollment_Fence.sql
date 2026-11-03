-- Install after the original V1-V16 and released immutable audit/completed-append scripts.
-- The installer supplies restricted login firefly_audit_writer and grants its completed accessor.
BEGIN;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='5s';
DO $$ BEGIN
    IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='firefly_audit_writer' AND rolcanlogin
        AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)
       OR pg_has_role('firefly_audit_writer','firefly_audit_guard','MEMBER')
       OR has_schema_privilege('firefly_audit_writer','public','CREATE')
       OR NOT EXISTS(SELECT FROM pg_proc p JOIN pg_roles r ON r.oid=p.proowner
           WHERE p.oid=to_regprocedure('public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean)')
           AND p.prosecdef AND p.provolatile='v' AND p.proconfig=ARRAY['search_path=pg_catalog'] AND r.rolname='firefly_audit_guard')
       OR NOT has_function_privilege('firefly_audit_writer','public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean)','EXECUTE') THEN
        RAISE EXCEPTION 'Original restricted completed audit deployment required' USING ERRCODE='42501';
    END IF;
    IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='accounts_card_fence_guard') THEN
        CREATE ROLE accounts_card_fence_guard NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='accounts_card_fence_reader') THEN
        CREATE ROLE accounts_card_fence_reader NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF EXISTS(SELECT FROM pg_roles WHERE rolname IN('accounts_card_fence_guard','accounts_card_fence_reader')
        AND (rolcanlogin OR rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication))
       OR pg_has_role('firefly_audit_writer','accounts_card_fence_guard','MEMBER') THEN
        RAISE EXCEPTION 'Restricted account fence roles required' USING ERRCODE='42501';
    END IF;
END $$;

CREATE SCHEMA accounts_card_fence_private AUTHORIZATION accounts_card_fence_guard;
REVOKE ALL ON SCHEMA accounts_card_fence_private FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO accounts_card_fence_guard,accounts_card_fence_reader,firefly_audit_writer;

CREATE TABLE public.account_card_enrollment_fence (
    action_id UUID PRIMARY KEY,receipt_id UUID NOT NULL UNIQUE,tenant_id UUID NOT NULL,actor_id UUID NOT NULL,
    party_id UUID NOT NULL,account_id UUID NOT NULL REFERENCES public.account(account_id),
    contract_id UUID NOT NULL,mandate_id UUID NOT NULL REFERENCES public.servicing_mandate(servicing_mandate_id),
    account_version BIGINT NOT NULL CHECK(account_version>=0),currency TEXT NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
    reason_code TEXT NOT NULL CHECK(reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    accept_not_after_epoch_millis BIGINT NOT NULL CHECK(accept_not_after_epoch_millis>0),recorded_at TIMESTAMPTZ NOT NULL,
    state TEXT NOT NULL CHECK(state IN('HELD','CLAIMED','CANCELLED')),version BIGINT NOT NULL CHECK(version IN(0,1)),
    claim_action_id UUID,cancellation_action_id UUID,
    CHECK((state='HELD' AND version=0 AND claim_action_id IS NULL AND cancellation_action_id IS NULL)
        OR (state='CLAIMED' AND version=1 AND claim_action_id IS NOT NULL AND cancellation_action_id IS NULL)
        OR (state='CANCELLED' AND version=1 AND claim_action_id IS NULL AND cancellation_action_id IS NOT NULL)),
    CHECK('00000000-0000-0000-0000-000000000000'::uuid<>ALL(ARRAY[action_id,receipt_id,tenant_id,actor_id,party_id,account_id,contract_id,mandate_id]))
);
CREATE UNIQUE INDEX account_card_pending_fence ON public.account_card_enrollment_fence(account_id) WHERE state IN('HELD','CLAIMED');
CREATE TABLE public.account_card_enrollment_action (
    operation_action_id UUID PRIMARY KEY,operation_receipt_id UUID NOT NULL UNIQUE,
    fence_action_id UUID NOT NULL REFERENCES public.account_card_enrollment_fence(action_id),fence_receipt_id UUID NOT NULL,
    tenant_id UUID NOT NULL,actor_id UUID NOT NULL,party_id UUID NOT NULL,account_id UUID NOT NULL,
    operation TEXT NOT NULL CHECK(operation IN('ACQUIRE','CLAIM','CANCEL')),expected_version BIGINT NOT NULL CHECK(expected_version>=0),
    reason_code TEXT NOT NULL CHECK(reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    resulting_state TEXT NOT NULL CHECK(resulting_state IN('HELD','CLAIMED','CANCELLED')),
    resulting_version BIGINT NOT NULL CHECK(resulting_version IN(0,1)),recorded_at TIMESTAMPTZ NOT NULL,event_id UUID NOT NULL UNIQUE,
    actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,actor_client_id TEXT NOT NULL,platform_tenant_id TEXT NOT NULL,policy_revision TEXT NOT NULL,
    CHECK('00000000-0000-0000-0000-000000000000'::uuid<>ALL(ARRAY[operation_action_id,operation_receipt_id,fence_action_id,fence_receipt_id,tenant_id,actor_id,party_id,account_id,event_id]))
);
CREATE TABLE public.account_card_enrollment_audit (
    operation_action_id UUID PRIMARY KEY REFERENCES public.account_card_enrollment_action(operation_action_id),tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,audit_context_nonce UUID NOT NULL,audit_sequence BIGINT NOT NULL CHECK(audit_sequence>0),
    audit_record_hash TEXT NOT NULL CHECK((audit_record_hash COLLATE "C") ~ '^[0-9a-f]{64}$'),
    FOREIGN KEY(tenant_id,event_id) REFERENCES firefly_audit.audit_event(resource_tenant_id,event_id),
    CHECK('00000000-0000-0000-0000-000000000000'::uuid<>ALL(ARRAY[operation_action_id,tenant_id,event_id,audit_context_nonce]))
);
CREATE TABLE accounts_card_fence_private.context (
    xid XID8 PRIMARY KEY,backend_pid INTEGER NOT NULL,login_name NAME NOT NULL,nonce UUID NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,actor_id UUID NOT NULL,party_id UUID NOT NULL,account_id UUID NOT NULL,
    fence_action_id UUID NOT NULL,operation_action_id UUID NOT NULL,purpose TEXT NOT NULL,
    receipt_id UUID,expected_version BIGINT,reason_code TEXT,accept_not_after_epoch_millis BIGINT,
    issuer TEXT NOT NULL,subject TEXT NOT NULL,client_id TEXT NOT NULL,platform_tenant_id TEXT NOT NULL,policy_revision TEXT NOT NULL,
    authority_until TIMESTAMPTZ NOT NULL,command_until TIMESTAMPTZ NOT NULL,database_time TIMESTAMPTZ NOT NULL,
    original_action_existed BOOLEAN NOT NULL,original_binding_existed BOOLEAN NOT NULL,
    original_fence_existed BOOLEAN NOT NULL,original_state TEXT,original_version BIGINT,audit_paired BOOLEAN NOT NULL DEFAULT false
);
ALTER TABLE accounts_card_fence_private.context OWNER TO accounts_card_fence_guard;
REVOKE ALL ON accounts_card_fence_private.context FROM PUBLIC;

GRANT SELECT ON public.account,public.account_money_enrollment,public.servicing_mandate TO accounts_card_fence_guard,accounts_card_fence_reader,firefly_audit_writer;
-- Row-lock privileges do not grant a permitted UPDATE: existing RLS and guard triggers remain active.
GRANT UPDATE(account_id) ON public.account TO accounts_card_fence_guard;
GRANT UPDATE(account_id) ON public.account_money_enrollment TO accounts_card_fence_guard;
GRANT SELECT ON public.account_card_enrollment_fence,public.account_card_enrollment_action,public.account_card_enrollment_audit TO accounts_card_fence_guard,accounts_card_fence_reader,firefly_audit_writer;
GRANT INSERT ON public.account_card_enrollment_fence,public.account_card_enrollment_action,public.account_card_enrollment_audit TO firefly_audit_writer;
GRANT UPDATE(state,version,claim_action_id,cancellation_action_id) ON public.account_card_enrollment_fence TO firefly_audit_writer;
GRANT EXECUTE ON FUNCTION public.firefly_audit_require_completed(UUID,UUID,BIGINT,TEXT,BOOLEAN) TO accounts_card_fence_guard;

CREATE FUNCTION public.accounts_card_fence_matches(p_tenant UUID,p_actor UUID,p_party UUID,p_account UUID,p_fence UUID) RETURNS BOOLEAN
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT FROM accounts_card_fence_private.context c WHERE c.xid=pg_current_xact_id_if_assigned()
        AND c.backend_pid=pg_backend_pid() AND c.login_name=session_user
        AND (c.tenant_id,c.actor_id,c.party_id,c.account_id,c.fence_action_id)=(p_tenant,p_actor,p_party,p_account,p_fence)
        AND c.authority_until>clock_timestamp() AND c.command_until>clock_timestamp())
$$;
CREATE FUNCTION public.accounts_card_fence_action_matches(p_tenant UUID,p_action UUID) RETURNS BOOLEAN
LANGUAGE sql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT EXISTS(SELECT FROM accounts_card_fence_private.context c WHERE c.xid=pg_current_xact_id_if_assigned()
        AND c.backend_pid=pg_backend_pid() AND c.login_name=session_user AND c.tenant_id=p_tenant AND c.operation_action_id=p_action
        AND c.authority_until>clock_timestamp() AND c.command_until>clock_timestamp())
$$;
ALTER FUNCTION public.accounts_card_fence_matches(UUID,UUID,UUID,UUID,UUID) OWNER TO accounts_card_fence_guard;
ALTER FUNCTION public.accounts_card_fence_action_matches(UUID,UUID) OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION public.accounts_card_fence_matches(UUID,UUID,UUID,UUID,UUID),public.accounts_card_fence_action_matches(UUID,UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.accounts_card_fence_matches(UUID,UUID,UUID,UUID,UUID),public.accounts_card_fence_action_matches(UUID,UUID) TO firefly_audit_writer,accounts_card_fence_reader;

ALTER TABLE public.account_card_enrollment_fence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_card_enrollment_fence FORCE ROW LEVEL SECURITY;
ALTER TABLE public.account_card_enrollment_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_card_enrollment_action FORCE ROW LEVEL SECURITY;
ALTER TABLE public.account_card_enrollment_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_card_enrollment_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY guard_read ON public.account_card_enrollment_fence FOR SELECT TO accounts_card_fence_guard USING(true);
CREATE POLICY guard_read ON public.account_card_enrollment_action FOR SELECT TO accounts_card_fence_guard USING(true);
CREATE POLICY guard_read ON public.account_card_enrollment_audit FOR SELECT TO accounts_card_fence_guard USING(true);
CREATE POLICY scoped_read ON public.account_card_enrollment_fence FOR SELECT TO firefly_audit_writer,accounts_card_fence_reader
    USING(public.accounts_card_fence_matches(tenant_id,actor_id,party_id,account_id,action_id));
CREATE POLICY scoped_insert ON public.account_card_enrollment_fence FOR INSERT TO firefly_audit_writer
    WITH CHECK(public.accounts_card_fence_matches(tenant_id,actor_id,party_id,account_id,action_id));
CREATE POLICY scoped_update ON public.account_card_enrollment_fence FOR UPDATE TO firefly_audit_writer
    USING(public.accounts_card_fence_matches(tenant_id,actor_id,party_id,account_id,action_id))
    WITH CHECK(public.accounts_card_fence_matches(tenant_id,actor_id,party_id,account_id,action_id));
CREATE POLICY scoped_read ON public.account_card_enrollment_action FOR SELECT TO firefly_audit_writer,accounts_card_fence_reader
    USING(public.accounts_card_fence_action_matches(tenant_id,operation_action_id));
CREATE POLICY scoped_insert ON public.account_card_enrollment_action FOR INSERT TO firefly_audit_writer
    WITH CHECK(public.accounts_card_fence_action_matches(tenant_id,operation_action_id));
CREATE POLICY scoped_read ON public.account_card_enrollment_audit FOR SELECT TO firefly_audit_writer,accounts_card_fence_reader
    USING(public.accounts_card_fence_action_matches(tenant_id,operation_action_id));
CREATE POLICY scoped_insert ON public.account_card_enrollment_audit FOR INSERT TO firefly_audit_writer
    WITH CHECK(public.accounts_card_fence_action_matches(tenant_id,operation_action_id));

CREATE FUNCTION public.accounts_card_fence_enter(p_nonce UUID,p_tenant UUID,p_actor UUID,p_party UUID,p_account UUID,p_fence UUID,p_action UUID,
    p_purpose TEXT,p_receipt UUID,p_version BIGINT,p_reason TEXT,p_deadline BIGINT,p_issuer TEXT,p_subject TEXT,p_client TEXT,p_platform TEXT,p_policy TEXT,
    p_authority_until TIMESTAMPTZ,p_command_until TIMESTAMPTZ)
RETURNS TABLE(context_nonce UUID,database_time TIMESTAMPTZ,command_until TIMESTAMPTZ)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE f public.account_card_enrollment_fence%ROWTYPE;at_time TIMESTAMPTZ;until_time TIMESTAMPTZ;
BEGIN
    IF p_purpose IS NULL OR p_purpose NOT IN('ACQUIRE','CLAIM','CANCEL','READ')
        OR (p_purpose<>'READ' AND session_user<>'firefly_audit_writer')
        OR (p_purpose='READ' AND NOT pg_has_role(session_user,'accounts_card_fence_reader','MEMBER'))
        OR EXISTS(SELECT FROM pg_roles WHERE rolname=session_user AND (rolsuper OR rolbypassrls OR rolcreatedb OR rolcreaterole OR rolreplication))
        OR pg_has_role(session_user,'accounts_card_fence_guard','MEMBER')
        OR p_nonce IS NULL OR p_tenant IS NULL OR p_actor IS NULL OR p_party IS NULL OR p_account IS NULL OR p_fence IS NULL OR p_action IS NULL
        OR '00000000-0000-0000-0000-000000000000'::uuid=ANY(ARRAY[p_nonce,p_tenant,p_actor,p_party,p_account,p_fence,p_action])
        OR NULLIF(p_issuer,'') IS NULL OR NULLIF(p_subject,'') IS NULL OR NULLIF(p_client,'') IS NULL OR NULLIF(p_platform,'') IS NULL OR NULLIF(p_policy,'') IS NULL
        OR p_authority_until IS NULL OR p_command_until IS NULL THEN
        RAISE EXCEPTION 'Current restricted account card fence context required' USING ERRCODE='42501';
    END IF;
    PERFORM set_config('firefly.funds.tenant',p_tenant::text,true);
    PERFORM set_config('firefly.funds.accounts',jsonb_build_array(p_account::text)::text,true);
    -- PostgreSQL row locks also evaluate the existing UPDATE policy. Only this
    -- non-login guard locks rows; the audit login has no account UPDATE grant.
    PERFORM set_config('firefly.funds.purpose','CONTROL',true);
    -- Canonical account first, shared enrollment gate second. No separate competing lock namespace.
    PERFORM 1 FROM public.account WHERE account_id=p_account FOR NO KEY UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Current account scope required' USING ERRCODE='42501';END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(p_account::text,55601));
    PERFORM 1 FROM public.account_money_enrollment WHERE account_id=p_account AND tenant_id=p_tenant::text FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Current managed account scope required' USING ERRCODE='42501';END IF;
    PERFORM set_config('firefly.funds.purpose','VERIFY',true);
    SELECT * INTO f FROM public.account_card_enrollment_fence WHERE action_id=p_fence;
    IF FOUND AND (f.tenant_id,f.actor_id,f.party_id,f.account_id) IS DISTINCT FROM (p_tenant,p_actor,p_party,p_account) THEN
        RAISE EXCEPTION 'Current original account fence owner required' USING ERRCODE='42501';
    END IF;
    at_time:=date_trunc('milliseconds',clock_timestamp());until_time:=LEAST(p_authority_until,p_command_until,at_time+interval '4 seconds');
    IF at_time>=until_time THEN RAISE EXCEPTION 'Current account fence deadline required' USING ERRCODE='42501';END IF;
    INSERT INTO accounts_card_fence_private.context(xid,backend_pid,login_name,nonce,tenant_id,actor_id,party_id,account_id,
        fence_action_id,operation_action_id,purpose,receipt_id,expected_version,reason_code,accept_not_after_epoch_millis,
        issuer,subject,client_id,platform_tenant_id,policy_revision,authority_until,command_until,database_time,
        original_action_existed,original_binding_existed,original_fence_existed,original_state,original_version)
    VALUES(pg_current_xact_id(),pg_backend_pid(),session_user,p_nonce,p_tenant,p_actor,p_party,p_account,p_fence,p_action,
        p_purpose,p_receipt,p_version,p_reason,p_deadline,p_issuer,p_subject,p_client,p_platform,p_policy,p_authority_until,until_time,at_time,
        EXISTS(SELECT FROM public.account_card_enrollment_action WHERE operation_action_id=p_action),
        EXISTS(SELECT FROM public.account_card_enrollment_audit WHERE operation_action_id=p_action),
        f.action_id IS NOT NULL,f.state,f.version);
    RETURN QUERY SELECT p_nonce,at_time,until_time;
END $$;
ALTER FUNCTION public.accounts_card_fence_enter(UUID,UUID,UUID,UUID,UUID,UUID,UUID,TEXT,UUID,BIGINT,TEXT,BIGINT,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ,TIMESTAMPTZ) OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION public.accounts_card_fence_enter(UUID,UUID,UUID,UUID,UUID,UUID,UUID,TEXT,UUID,BIGINT,TEXT,BIGINT,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ,TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.accounts_card_fence_enter(UUID,UUID,UUID,UUID,UUID,UUID,UUID,TEXT,UUID,BIGINT,TEXT,BIGINT,TEXT,TEXT,TEXT,TEXT,TEXT,TIMESTAMPTZ,TIMESTAMPTZ) TO firefly_audit_writer,accounts_card_fence_reader;

CREATE FUNCTION accounts_card_fence_private.require_owner(c accounts_card_fence_private.context,f public.account_card_enrollment_fence) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF NOT EXISTS(SELECT FROM public.account a JOIN public.account_money_enrollment e ON e.account_id=a.account_id
        JOIN public.servicing_mandate m ON m.account_id=a.account_id WHERE a.account_id=c.account_id
        AND e.tenant_id=c.tenant_id::text AND e.contract_id=a.contract_id AND e.contract_id=f.contract_id
        AND e.currency=a.currency AND e.currency=f.currency AND e.kind='CUSTOMER' AND e.mode='MANAGED' AND e.control='ACTIVE'
        AND e.version=f.account_version AND e.pending_action_id IS NULL AND a.account_status='OPEN' AND a.close_date IS NULL
        AND m.servicing_mandate_id=f.mandate_id AND m.party_id=c.party_id AND m.active AND m.role='OWNER')
        OR f.accept_not_after_epoch_millis<=floor(extract(epoch FROM clock_timestamp())*1000)::bigint THEN
        RAISE EXCEPTION 'Current account owner and original eligibility bound required' USING ERRCODE='23514';
    END IF;
END $$;
ALTER FUNCTION accounts_card_fence_private.require_owner(accounts_card_fence_private.context,public.account_card_enrollment_fence) OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION accounts_card_fence_private.require_owner(accounts_card_fence_private.context,public.account_card_enrollment_fence) FROM PUBLIC;

CREATE FUNCTION accounts_card_fence_private.guard_write() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c accounts_card_fence_private.context%ROWTYPE;f public.account_card_enrollment_fence%ROWTYPE;
BEGIN
    SELECT * INTO c FROM accounts_card_fence_private.context WHERE xid=pg_current_xact_id_if_assigned()
        AND backend_pid=pg_backend_pid() AND login_name=session_user;
    IF NOT FOUND OR session_user<>'firefly_audit_writer' OR c.purpose='READ' OR c.original_action_existed
        OR c.original_binding_existed OR c.audit_paired OR c.command_until<=clock_timestamp() OR c.authority_until<=clock_timestamp()
        OR TG_OP='DELETE' OR (TG_TABLE_NAME<>'account_card_enrollment_fence' AND TG_OP<>'INSERT') THEN
        RAISE EXCEPTION 'Original mapped account fence command required' USING ERRCODE='42501';
    END IF;
    IF TG_TABLE_NAME='account_card_enrollment_fence' THEN
        IF (NEW.tenant_id,NEW.actor_id,NEW.party_id,NEW.account_id,NEW.action_id) IS DISTINCT FROM (c.tenant_id,c.actor_id,c.party_id,c.account_id,c.fence_action_id) THEN
            RAISE EXCEPTION 'Exact original fence scope required' USING ERRCODE='23514';
        END IF;
        IF TG_OP='INSERT' THEN
            IF c.purpose<>'ACQUIRE' OR c.original_fence_existed OR c.fence_action_id<>c.operation_action_id
                OR (NEW.state,NEW.version,NEW.account_version,NEW.reason_code,NEW.accept_not_after_epoch_millis,NEW.recorded_at)
                    IS DISTINCT FROM ('HELD'::text,0::bigint,c.expected_version,c.reason_code,c.accept_not_after_epoch_millis,c.database_time) THEN
                RAISE EXCEPTION 'Exact original fence acquisition required' USING ERRCODE='23514';
            END IF;
            PERFORM accounts_card_fence_private.require_owner(c,NEW);
        ELSE
            IF NOT c.original_fence_existed OR c.original_state<>'HELD' OR OLD.state<>'HELD' OR OLD.version<>c.expected_version
                OR NEW.receipt_id<>c.receipt_id OR NEW.version<>OLD.version+1
                OR (to_jsonb(NEW)-ARRAY['state','version','claim_action_id','cancellation_action_id'])
                    IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','version','claim_action_id','cancellation_action_id'])
                OR NOT ((c.purpose='CLAIM' AND NEW.state='CLAIMED' AND NEW.claim_action_id=c.operation_action_id AND NEW.cancellation_action_id IS NULL)
                    OR (c.purpose='CANCEL' AND NEW.state='CANCELLED' AND NEW.cancellation_action_id=c.operation_action_id AND NEW.claim_action_id IS NULL)) THEN
                RAISE EXCEPTION 'Atomic original claim or cancellation required' USING ERRCODE='23514';
            END IF;
            IF c.purpose='CLAIM' THEN PERFORM accounts_card_fence_private.require_owner(c,NEW);END IF;
        END IF;
    ELSIF TG_TABLE_NAME='account_card_enrollment_action' THEN
        SELECT * INTO STRICT f FROM public.account_card_enrollment_fence WHERE action_id=c.fence_action_id;
        IF (NEW.tenant_id,NEW.actor_id,NEW.party_id,NEW.account_id,NEW.fence_action_id,NEW.fence_receipt_id,NEW.operation_action_id,
            NEW.operation,NEW.expected_version,NEW.reason_code,NEW.resulting_state,NEW.resulting_version,NEW.recorded_at,
            NEW.actor_issuer,NEW.actor_subject,NEW.actor_client_id,NEW.platform_tenant_id,NEW.policy_revision)
            IS DISTINCT FROM (c.tenant_id,c.actor_id,c.party_id,c.account_id,c.fence_action_id,f.receipt_id,c.operation_action_id,
                c.purpose,c.expected_version,c.reason_code,f.state,f.version,c.database_time,c.issuer,c.subject,c.client_id,c.platform_tenant_id,c.policy_revision) THEN
            RAISE EXCEPTION 'Exact immutable operation receipt required' USING ERRCODE='23514';
        END IF;
    ELSIF (NEW.operation_action_id,NEW.tenant_id) IS DISTINCT FROM (c.operation_action_id,c.tenant_id) THEN
        RAISE EXCEPTION 'Exact immutable audit binding required' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
ALTER FUNCTION accounts_card_fence_private.guard_write() OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION accounts_card_fence_private.guard_write() FROM PUBLIC;
CREATE TRIGGER account_card_fence_write BEFORE INSERT OR UPDATE OR DELETE ON public.account_card_enrollment_fence FOR EACH ROW EXECUTE FUNCTION accounts_card_fence_private.guard_write();
CREATE TRIGGER account_card_action_write BEFORE INSERT OR UPDATE OR DELETE ON public.account_card_enrollment_action FOR EACH ROW EXECUTE FUNCTION accounts_card_fence_private.guard_write();
CREATE TRIGGER account_card_audit_write BEFORE INSERT OR UPDATE OR DELETE ON public.account_card_enrollment_audit FOR EACH ROW EXECUTE FUNCTION accounts_card_fence_private.guard_write();

CREATE FUNCTION accounts_card_fence_private.guard_control() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE a UUID;
BEGIN
    a:=OLD.account_id;
    PERFORM 1 FROM public.account WHERE account_id=a FOR NO KEY UPDATE;
    PERFORM pg_advisory_xact_lock(hashtextextended(a::text,55601));
    IF EXISTS(SELECT FROM public.account_card_enrollment_fence WHERE account_id=a AND state IN('HELD','CLAIMED')) THEN
        IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Pending card enrollment requires resolution' USING ERRCODE='23514';END IF;
        IF TG_TABLE_NAME='account_money_enrollment' THEN
            IF NEW.control NOT IN('BLOCKING','BLOCKED') OR NEW.version<>OLD.version+1
                OR (to_jsonb(NEW)-ARRAY['control','version','pending_action_id']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['control','version','pending_action_id']) THEN
                RAISE EXCEPTION 'Pending card enrollment permits security blocking only' USING ERRCODE='23514';
            END IF;
        ELSIF NEW.account_status<>'SUSPENDED'
            OR (to_jsonb(NEW)-ARRAY['account_status','date_updated']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['account_status','date_updated']) THEN
            RAISE EXCEPTION 'Pending card enrollment prevents account closure or rebinding' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD;END IF;RETURN NEW;
END $$;
ALTER FUNCTION accounts_card_fence_private.guard_control() OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION accounts_card_fence_private.guard_control() FROM PUBLIC;
CREATE TRIGGER account_card_control_fence BEFORE UPDATE OR DELETE ON public.account_money_enrollment FOR EACH ROW EXECUTE FUNCTION accounts_card_fence_private.guard_control();
CREATE TRIGGER account_card_canonical_fence BEFORE UPDATE OR DELETE ON public.account FOR EACH ROW EXECUTE FUNCTION accounts_card_fence_private.guard_control();

CREATE FUNCTION accounts_card_fence_private.require_completion() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF EXISTS(SELECT FROM accounts_card_fence_private.context WHERE xid=NEW.xid AND nonce=NEW.nonce) THEN
        RAISE EXCEPTION 'Completed canonical account fence audit required before commit' USING ERRCODE='23514';
    END IF;RETURN NULL;
END $$;
ALTER FUNCTION accounts_card_fence_private.require_completion() OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION accounts_card_fence_private.require_completion() FROM PUBLIC;
CREATE CONSTRAINT TRIGGER account_card_fence_completion AFTER INSERT ON accounts_card_fence_private.context
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION accounts_card_fence_private.require_completion();

CREATE FUNCTION accounts_card_fence_private.audit_text(p_text TEXT) RETURNS BYTEA
LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
    SELECT int4send(octet_length(convert_to(p_text,'UTF8')))||convert_to(p_text,'UTF8')
$$;
CREATE FUNCTION accounts_card_fence_private.evidence(f public.account_card_enrollment_fence,a public.account_card_enrollment_action) RETURNS TEXT
LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
    SELECT encode(sha256(accounts_card_fence_private.audit_text('accounts.card.enrollment.evidence')||int4send(1)
        ||uuid_send(f.tenant_id)||uuid_send(f.actor_id)||uuid_send(f.party_id)||uuid_send(f.account_id)||uuid_send(f.contract_id)||uuid_send(f.mandate_id)
        ||uuid_send(f.action_id)||uuid_send(f.receipt_id)||uuid_send(a.operation_action_id)||uuid_send(a.operation_receipt_id)||uuid_send(a.event_id)
        ||int8send(f.account_version)||int8send(f.accept_not_after_epoch_millis)
        ||int8send(floor(extract(epoch FROM f.recorded_at))::bigint)||int4send(((extract(epoch FROM f.recorded_at)-floor(extract(epoch FROM f.recorded_at)))*1000000000)::integer)
        ||int8send(a.expected_version)||int8send(a.resulting_version)
        ||int8send(floor(extract(epoch FROM a.recorded_at))::bigint)||int4send(((extract(epoch FROM a.recorded_at)-floor(extract(epoch FROM a.recorded_at)))*1000000000)::integer)
        ||accounts_card_fence_private.audit_text(f.currency)||accounts_card_fence_private.audit_text(f.reason_code)
        ||accounts_card_fence_private.audit_text(a.operation)||accounts_card_fence_private.audit_text(a.reason_code)||accounts_card_fence_private.audit_text(a.resulting_state)
        ||accounts_card_fence_private.audit_text(a.actor_issuer)||accounts_card_fence_private.audit_text(a.actor_subject)
        ||accounts_card_fence_private.audit_text(a.actor_client_id)||accounts_card_fence_private.audit_text(a.platform_tenant_id)||accounts_card_fence_private.audit_text(a.policy_revision)),'hex')
$$;
ALTER FUNCTION accounts_card_fence_private.audit_text(TEXT) OWNER TO accounts_card_fence_guard;
ALTER FUNCTION accounts_card_fence_private.evidence(public.account_card_enrollment_fence,public.account_card_enrollment_action) OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION accounts_card_fence_private.audit_text(TEXT),accounts_card_fence_private.evidence(public.account_card_enrollment_fence,public.account_card_enrollment_action) FROM PUBLIC;

CREATE FUNCTION public.accounts_card_fence_pair_audit(p_nonce UUID,p_audit_nonce UUID,p_event UUID,p_sequence BIGINT,p_hash TEXT,p_replay BOOLEAN) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c accounts_card_fence_private.context%ROWTYPE;f public.account_card_enrollment_fence%ROWTYPE;
    a public.account_card_enrollment_action%ROWTYPE;b public.account_card_enrollment_audit%ROWTYPE;completed RECORD;
BEGIN
    SELECT * INTO c FROM accounts_card_fence_private.context WHERE xid=pg_current_xact_id_if_assigned()
        AND backend_pid=pg_backend_pid() AND login_name=session_user AND nonce=p_nonce;
    IF NOT FOUND OR session_user<>'firefly_audit_writer' OR c.purpose='READ' OR c.audit_paired
        OR c.original_action_existed<>c.original_binding_existed OR p_replay IS DISTINCT FROM c.original_action_existed
        OR c.command_until<=clock_timestamp() OR c.authority_until<=clock_timestamp() THEN
        RAISE EXCEPTION 'Original completed account fence audit disposition required' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT f FROM public.account_card_enrollment_fence WHERE action_id=c.fence_action_id
        AND (tenant_id,actor_id,party_id,account_id)=(c.tenant_id,c.actor_id,c.party_id,c.account_id);
    SELECT * INTO STRICT a FROM public.account_card_enrollment_action WHERE operation_action_id=c.operation_action_id
        AND fence_action_id=f.action_id AND fence_receipt_id=f.receipt_id AND event_id=p_event
        AND (tenant_id,actor_id,party_id,account_id)=(c.tenant_id,c.actor_id,c.party_id,c.account_id)
        AND operation=c.purpose AND expected_version=c.expected_version AND reason_code=c.reason_code
        AND (actor_issuer,actor_subject,actor_client_id,platform_tenant_id)=(c.issuer,c.subject,c.client_id,c.platform_tenant_id);
    IF (c.purpose='ACQUIRE' AND (f.account_version,f.reason_code,f.accept_not_after_epoch_millis)
        IS DISTINCT FROM (c.expected_version,c.reason_code,c.accept_not_after_epoch_millis))
        OR (c.purpose IN('CLAIM','CANCEL') AND f.receipt_id IS DISTINCT FROM c.receipt_id)
        OR (NOT p_replay AND (a.resulting_state,a.resulting_version) IS DISTINCT FROM (f.state,f.version)) THEN
        RAISE EXCEPTION 'Exact retained account fence intent required' USING ERRCODE='23514';
    END IF;
    SELECT * INTO STRICT b FROM public.account_card_enrollment_audit WHERE operation_action_id=a.operation_action_id
        AND tenant_id=a.tenant_id AND event_id=a.event_id AND audit_sequence=p_sequence AND audit_record_hash=p_hash
        AND (p_replay OR audit_context_nonce=p_audit_nonce);
    SELECT * INTO STRICT completed FROM public.firefly_audit_require_completed(p_audit_nonce,p_event,p_sequence,p_hash,p_replay);
    IF (completed.resource_tenant_id,completed.service,completed.resource_type,completed.resource_id,completed.event_id,
        completed.actor_issuer,completed.actor_subject,completed.actor_client_id,completed.actor_platform_tenant_id,completed.actor_staff_realm,completed.actor_staff_id,
        completed.action,completed.outcome,completed.process_id,completed.correlation_id,completed.causation_id,completed.occurred_seconds,completed.occurred_nanos,completed.evidence_digest,completed.policy_revision)
        IS DISTINCT FROM (f.tenant_id,'core-banking-accounts'::text,'account-card-enrollment'::text,f.action_id,a.event_id,
            a.actor_issuer,a.actor_subject,a.actor_client_id,a.platform_tenant_id,NULL::text,NULL::uuid,
            'ACCOUNT_CARD_ENROLLMENT_'||a.operation,'COMPLETED'::text,f.action_id,a.operation_receipt_id,a.operation_action_id,
            floor(extract(epoch FROM a.recorded_at))::bigint,((extract(epoch FROM a.recorded_at)-floor(extract(epoch FROM a.recorded_at)))*1000000000)::integer,
            accounts_card_fence_private.evidence(f,a),a.policy_revision) THEN
        RAISE EXCEPTION 'Exact completed original canonical account fence audit required' USING ERRCODE='23514';
    END IF;
    UPDATE accounts_card_fence_private.context SET audit_paired=true WHERE xid=c.xid AND nonce=c.nonce;
    RETURN p_nonce;
END $$;
ALTER FUNCTION public.accounts_card_fence_pair_audit(UUID,UUID,UUID,BIGINT,TEXT,BOOLEAN) OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION public.accounts_card_fence_pair_audit(UUID,UUID,UUID,BIGINT,TEXT,BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.accounts_card_fence_pair_audit(UUID,UUID,UUID,BIGINT,TEXT,BOOLEAN) TO firefly_audit_writer;

CREATE FUNCTION public.accounts_card_fence_complete(p_nonce UUID) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c accounts_card_fence_private.context%ROWTYPE;
BEGIN
    SELECT * INTO c FROM accounts_card_fence_private.context WHERE xid=pg_current_xact_id_if_assigned()
        AND backend_pid=pg_backend_pid() AND login_name=session_user AND nonce=p_nonce;
    IF NOT FOUND OR c.command_until<=clock_timestamp() OR c.authority_until<=clock_timestamp()
        OR (c.purpose<>'READ' AND NOT c.audit_paired)
        OR (c.purpose='READ' AND (NOT c.original_action_existed OR NOT c.original_binding_existed))
        OR NOT EXISTS(SELECT FROM public.account_card_enrollment_fence f
            JOIN public.account_card_enrollment_action a ON a.fence_action_id=f.action_id AND a.fence_receipt_id=f.receipt_id
            JOIN public.account_card_enrollment_audit b ON b.operation_action_id=a.operation_action_id AND b.tenant_id=a.tenant_id AND b.event_id=a.event_id
            WHERE f.action_id=c.fence_action_id AND a.operation_action_id=c.operation_action_id
                AND (f.tenant_id,f.actor_id,f.party_id,f.account_id)=(c.tenant_id,c.actor_id,c.party_id,c.account_id)
                AND (a.tenant_id,a.actor_id,a.party_id,a.account_id)=(c.tenant_id,c.actor_id,c.party_id,c.account_id)) THEN
        RAISE EXCEPTION 'Complete original account fence receipt and audit required' USING ERRCODE='42501';
    END IF;
    DELETE FROM accounts_card_fence_private.context WHERE xid=c.xid AND nonce=c.nonce;
    RETURN p_nonce;
END $$;
ALTER FUNCTION public.accounts_card_fence_complete(UUID) OWNER TO accounts_card_fence_guard;
REVOKE ALL ON FUNCTION public.accounts_card_fence_complete(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.accounts_card_fence_complete(UUID) TO firefly_audit_writer,accounts_card_fence_reader;
COMMIT;
