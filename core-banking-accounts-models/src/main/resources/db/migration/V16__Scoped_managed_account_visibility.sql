CREATE SCHEMA IF NOT EXISTS funds_private;
REVOKE ALL ON SCHEMA funds_private FROM PUBLIC;
CREATE TABLE funds_private.visibility (
    kind VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    accounts UUID[] NOT NULL,
    PRIMARY KEY(kind,tenant_id,resource_id)
);
REVOKE ALL ON funds_private.visibility FROM PUBLIC;
CREATE FUNCTION public.funds_scope_matches(t TEXT,a UUID[],writes BOOLEAN DEFAULT FALSE)
RETURNS BOOLEAN LANGUAGE SQL STABLE SET search_path=pg_catalog AS $$
 SELECT COALESCE(current_setting('firefly.funds.tenant',true)=t
   AND current_setting('firefly.funds.purpose',true) IN ('ENROLL','LINK','VERIFY','CONTROL')
   AND (NOT writes OR current_setting('firefly.funds.purpose',true) IN ('ENROLL','LINK','CONTROL'))
   AND cardinality(a)>0
   AND COALESCE(NULLIF(current_setting('firefly.funds.accounts',true),''),'[]')::jsonb ?& ARRAY(SELECT x::text FROM unnest(a) x),FALSE)
$$;
CREATE FUNCTION public.funds_reference_allowed(k TEXT,r UUID,w BOOLEAN DEFAULT FALSE)
RETURNS BOOLEAN LANGUAGE SQL STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT NOT EXISTS(SELECT 1 FROM funds_private.visibility v WHERE v.kind=k AND v.resource_id=r)
   OR EXISTS(SELECT 1 FROM funds_private.visibility v WHERE v.kind=k AND v.resource_id=r AND public.funds_scope_matches(v.tenant_id,v.accounts,w))
$$;
CREATE FUNCTION public.stamp_account_funds_visibility() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 INSERT INTO funds_private.visibility VALUES('ACCOUNT',NEW.tenant_id,NEW.account_id,ARRAY[NEW.account_id]) ON CONFLICT DO NOTHING;
 INSERT INTO funds_private.visibility(kind,tenant_id,resource_id,accounts)
   SELECT 'SPACE',NEW.tenant_id,s.account_space_id,ARRAY[NEW.account_id] FROM public.account_space s WHERE s.account_id=NEW.account_id ON CONFLICT DO NOTHING;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.funds_scope_matches(TEXT,UUID[],BOOLEAN),public.funds_reference_allowed(TEXT,UUID,BOOLEAN),public.stamp_account_funds_visibility() FROM PUBLIC;
-- Boolean policy predicates are intentionally executable by legacy and managed application queries.
GRANT EXECUTE ON FUNCTION public.funds_scope_matches(TEXT,UUID[],BOOLEAN),public.funds_reference_allowed(TEXT,UUID,BOOLEAN) TO PUBLIC;
CREATE TRIGGER account_funds_visibility AFTER INSERT ON public.account_money_enrollment FOR EACH ROW EXECUTE FUNCTION public.stamp_account_funds_visibility();

CREATE OR REPLACE FUNCTION public.guard_managed_account_write() RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE o JSONB; n JSONB; a UUID; ids UUID[];
BEGIN
 IF TG_OP<>'INSERT' THEN o:=to_jsonb(OLD); END IF;
 IF TG_OP<>'DELETE' THEN n:=to_jsonb(NEW); END IF;
 IF TG_TABLE_NAME='space_transaction' THEN
   SELECT array_agg(DISTINCT x ORDER BY x) INTO ids FROM (
     SELECT unnest(v.accounts) x FROM funds_private.visibility v WHERE v.kind='SPACE'
       AND v.resource_id IN((o->>'account_space_id')::uuid,(n->>'account_space_id')::uuid)
     UNION SELECT s.account_id x FROM public.account_space s
       WHERE s.account_space_id IN((o->>'account_space_id')::uuid,(n->>'account_space_id')::uuid)
   ) q;
 ELSE
   SELECT array_agg(DISTINCT x ORDER BY x) INTO ids FROM (
     SELECT (o->>'account_id')::uuid x UNION SELECT (n->>'account_id')::uuid
   ) q WHERE x IS NOT NULL;
 END IF;
 IF ids IS NOT NULL THEN
   FOREACH a IN ARRAY ids LOOP
     PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(a::text,55601));
   END LOOP;
   -- Recheck binding after every account gate is held; enrollment may have committed while waiting.
   FOREACH a IN ARRAY ids LOOP
     IF EXISTS(SELECT 1 FROM funds_private.visibility v WHERE v.kind='ACCOUNT' AND v.resource_id=a)
        AND (NOT public.funds_reference_allowed('ACCOUNT',a,TRUE)
             OR current_setting('firefly.funds.purpose',true)<>'CONTROL'
             OR TG_TABLE_NAME<>'account' AND NOT (TG_TABLE_NAME='account_status_history' AND TG_OP='INSERT')) THEN
       RAISE EXCEPTION 'MANAGED_MONEY_WORKFLOW_REQUIRED' USING ERRCODE='23514';
     END IF;
   END LOOP;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.guard_managed_account_write() FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_policy_reader' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin)
    AND EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_funds_policy_writer' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcanlogin) THEN
   GRANT USAGE ON SCHEMA funds_private TO firefly_funds_policy_reader,firefly_funds_policy_writer;
   GRANT SELECT ON funds_private.visibility TO firefly_funds_policy_reader;
   GRANT INSERT ON funds_private.visibility TO firefly_funds_policy_writer;
   GRANT SELECT(account_id,account_space_id) ON public.account_space TO firefly_funds_policy_writer,firefly_funds_policy_reader;
   GRANT CREATE ON SCHEMA public TO firefly_funds_policy_reader,firefly_funds_policy_writer;
   ALTER FUNCTION public.funds_reference_allowed(TEXT,UUID,BOOLEAN) OWNER TO firefly_funds_policy_reader;
   ALTER FUNCTION public.guard_managed_account_write() OWNER TO firefly_funds_policy_reader;
   ALTER FUNCTION public.stamp_account_funds_visibility() OWNER TO firefly_funds_policy_writer;
   REVOKE CREATE ON SCHEMA public FROM firefly_funds_policy_reader,firefly_funds_policy_writer;
 END IF;
END $$;

ALTER TABLE public.account ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));

ALTER TABLE public.account_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_balance FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_balance FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_balance FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_balance FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_balance FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));

ALTER TABLE public.account_space ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_space FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_space FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_space FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_space FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_space FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));

ALTER TABLE public.account_restriction ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_restriction FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_restriction FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_restriction FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_restriction FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_restriction FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));

ALTER TABLE public.account_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_status_history FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_status_history FOR SELECT USING (public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_status_history FOR INSERT WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_status_history FOR UPDATE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_status_history FOR DELETE USING (public.funds_reference_allowed('ACCOUNT',account_id,TRUE));

ALTER TABLE public.space_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.space_transaction FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.space_transaction FOR SELECT USING (public.funds_reference_allowed('SPACE',account_space_id,FALSE));
CREATE POLICY funds_insert ON public.space_transaction FOR INSERT WITH CHECK (public.funds_reference_allowed('SPACE',account_space_id,TRUE));
CREATE POLICY funds_update ON public.space_transaction FOR UPDATE USING (public.funds_reference_allowed('SPACE',account_space_id,TRUE)) WITH CHECK (public.funds_reference_allowed('SPACE',account_space_id,TRUE));
CREATE POLICY funds_delete ON public.space_transaction FOR DELETE USING (FALSE);

ALTER TABLE public.account_money_enrollment ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_money_enrollment FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_money_enrollment FOR SELECT USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],FALSE));
CREATE POLICY funds_insert ON public.account_money_enrollment FOR INSERT WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_update ON public.account_money_enrollment FOR UPDATE USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE)) WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_delete ON public.account_money_enrollment FOR DELETE USING (FALSE);

ALTER TABLE public.account_money_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_money_action FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_money_action FOR SELECT USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],FALSE));
CREATE POLICY funds_insert ON public.account_money_action FOR INSERT WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_update ON public.account_money_action FOR UPDATE USING (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE)) WITH CHECK (public.funds_scope_matches(tenant_id,ARRAY[account_id],TRUE));
CREATE POLICY funds_delete ON public.account_money_action FOR DELETE USING (FALSE);

ALTER TABLE public.account_money_action ADD COLUMN actor VARCHAR(200) NOT NULL;
ALTER TABLE public.account_money_action ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.servicing_mandate ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.servicing_mandate FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.servicing_mandate FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.servicing_mandate FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.servicing_mandate FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.servicing_mandate FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.servicing_mandate FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.account_statement ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_statement FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_statement FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_statement FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_statement FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_statement FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_statement FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.account_limit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_limit FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_limit FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_limit FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_limit FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_limit FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_limit FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.savings_interest ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.savings_interest FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.savings_interest FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.savings_interest FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.savings_interest FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.savings_interest FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.savings_interest FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.term_deposit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.term_deposit FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.term_deposit FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.term_deposit FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.term_deposit FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.term_deposit FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.term_deposit FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.account_sweep ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_sweep FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_sweep FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_sweep FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_sweep FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_sweep FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_sweep FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.account_parameter ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_parameter FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_parameter FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_parameter FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_parameter FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_parameter FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_parameter FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.account_provider ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_provider FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_provider FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_provider FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_provider FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_provider FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_provider FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

ALTER TABLE public.account_notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_notification FORCE ROW LEVEL SECURITY;
CREATE POLICY funds_select ON public.account_notification FOR SELECT USING(public.funds_reference_allowed('ACCOUNT',account_id,FALSE));
CREATE POLICY funds_insert ON public.account_notification FOR INSERT WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_update ON public.account_notification FOR UPDATE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE)) WITH CHECK(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE POLICY funds_delete ON public.account_notification FOR DELETE USING(public.funds_reference_allowed('ACCOUNT',account_id,TRUE));
CREATE TRIGGER managed_money_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_notification FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();

CREATE TRIGGER managed_status_history_fence BEFORE INSERT OR UPDATE OR DELETE ON public.account_status_history FOR EACH ROW EXECUTE FUNCTION public.guard_managed_account_write();
