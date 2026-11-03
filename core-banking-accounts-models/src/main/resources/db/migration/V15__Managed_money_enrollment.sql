CREATE TABLE account_money_enrollment (
    account_id UUID PRIMARY KEY REFERENCES account(account_id),
    tenant_id VARCHAR(64) NOT NULL,
    enrollment_id UUID NOT NULL,
    contract_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CUSTOMER','BANK_CONTROL')),
    mode VARCHAR(20) NOT NULL DEFAULT 'ENROLLING' CHECK (mode IN ('ENROLLING','MANAGED')),
    control VARCHAR(20) NOT NULL DEFAULT 'BLOCKED' CHECK(control IN ('ACTIVE','BLOCKING','BLOCKED','ENABLING','CLOSING','CLOSED')),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(tenant_id,enrollment_id)
);

CREATE FUNCTION guard_managed_account_write() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE a UUID; b UUID;
BEGIN
    IF TG_TABLE_NAME='account' THEN
        IF TG_OP<>'INSERT' THEN a:=OLD.account_id; END IF;
        IF TG_OP<>'DELETE' THEN b:=NEW.account_id; END IF;
    ELSIF TG_TABLE_NAME='space_transaction' THEN
        IF TG_OP<>'INSERT' THEN SELECT account_id INTO a FROM account_space WHERE account_space_id=OLD.account_space_id; END IF;
        IF TG_OP<>'DELETE' THEN SELECT account_id INTO b FROM account_space WHERE account_space_id=NEW.account_space_id; END IF;
    ELSE
        IF TG_OP<>'INSERT' THEN a:=OLD.account_id; END IF;
        IF TG_OP<>'DELETE' THEN b:=NEW.account_id; END IF;
    END IF;
    PERFORM 1 FROM account WHERE account_id IN(a,b) ORDER BY account_id FOR UPDATE;
    IF EXISTS(SELECT 1 FROM account_money_enrollment WHERE account_id IN(a,b)) THEN
        RAISE EXCEPTION 'MANAGED_MONEY_WORKFLOW_REQUIRED' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER managed_account_fence BEFORE INSERT OR UPDATE OR DELETE ON account FOR EACH ROW EXECUTE FUNCTION guard_managed_account_write();
CREATE TRIGGER managed_balance_fence BEFORE INSERT OR UPDATE OR DELETE ON account_balance FOR EACH ROW EXECUTE FUNCTION guard_managed_account_write();
CREATE TRIGGER managed_space_fence BEFORE INSERT OR UPDATE OR DELETE ON account_space FOR EACH ROW EXECUTE FUNCTION guard_managed_account_write();
CREATE TRIGGER managed_space_transaction_fence BEFORE INSERT OR UPDATE OR DELETE ON space_transaction FOR EACH ROW EXECUTE FUNCTION guard_managed_account_write();
CREATE TRIGGER managed_restriction_fence BEFORE INSERT OR UPDATE OR DELETE ON account_restriction FOR EACH ROW EXECUTE FUNCTION guard_managed_account_write();

ALTER TABLE account_money_enrollment ADD COLUMN pending_action_id UUID;
CREATE TABLE account_money_action (
    tenant_id VARCHAR(64) NOT NULL,
    action_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES account_money_enrollment(account_id),
    target VARCHAR(20) NOT NULL CHECK(target IN ('ACTIVE','BLOCKED','CLOSED')),
    expected_version BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','COMPLETE','REJECTED')),
    PRIMARY KEY(tenant_id,action_id)
);
