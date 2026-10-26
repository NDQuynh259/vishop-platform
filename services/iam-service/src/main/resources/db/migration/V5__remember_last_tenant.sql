-- Login preference only. Authorization must still validate current tenant membership.
ALTER TABLE users
    ADD COLUMN last_tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL;
