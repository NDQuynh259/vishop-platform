ALTER TABLE role_permissions DROP CONSTRAINT ck_tenant_permission;
ALTER TABLE role_permissions ADD CONSTRAINT ck_tenant_permission CHECK (permission_key IN (
        'order.view', 'order.create', 'order.update', 'order.cancel',
        'order.package.ship', 'order.print', 'order.split', 'order.export',
        'product.view', 'product.create', 'product.update', 'product.delete',
        'product.view_cost_price', 'product.edit_cost_price', 'product.link_channel',
        'inventory.view', 'inventory.adjust', 'inventory.transfer', 'inventory.stock_take',
        'tenant.view', 'tenant.manage', 'tenant.member.view', 'tenant.member.invite',
        'tenant.member.role_assign', 'tenant.member.remove',
        'shop.view', 'shop.connect', 'shop.disconnect', 'shop.settings',
        'finance.settlement.view', 'finance.settlement.export', 'finance.reconciliation',
        'audit_log.view', 'audit_log.export', 'billing.view', 'billing.manage'
    ));

CREATE OR REPLACE FUNCTION create_builtin_tenant_roles() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
DECLARE
    owner_role UUID;
BEGIN
    INSERT INTO public.roles (tenant_id, name, builtin_key) VALUES
        (NEW.id, 'Owner', 'OWNER'),
        (NEW.id, 'Operations', 'OPERATIONS'),
        (NEW.id, 'Warehouse', 'WAREHOUSE'),
        (NEW.id, 'Accounting', 'ACCOUNTING');

    SELECT id INTO owner_role FROM public.roles
        WHERE tenant_id = NEW.id AND builtin_key = 'OWNER';

    INSERT INTO public.role_permissions (role_id, permission_key)
    SELECT owner_role, unnest(ARRAY[
        'order.view', 'order.create', 'order.update', 'order.cancel',
        'order.package.ship', 'order.print', 'order.split', 'order.export',
        'product.view', 'product.create', 'product.update', 'product.delete',
        'product.view_cost_price', 'product.edit_cost_price', 'product.link_channel',
        'inventory.view', 'inventory.adjust', 'inventory.transfer', 'inventory.stock_take',
        'tenant.view', 'tenant.manage', 'tenant.member.view', 'tenant.member.invite',
        'tenant.member.role_assign', 'tenant.member.remove',
        'shop.view', 'shop.connect', 'shop.disconnect', 'shop.settings',
        'finance.settlement.view', 'finance.settlement.export', 'finance.reconciliation',
        'audit_log.view', 'audit_log.export', 'billing.view', 'billing.manage'
    ]);

    INSERT INTO public.role_permissions (role_id, permission_key)
    SELECT r.id, p.permission_key FROM public.roles r
    JOIN (VALUES
        ('OPERATIONS', ARRAY[
            'order.view', 'order.create', 'order.update', 'order.cancel',
            'order.package.ship', 'order.print', 'order.split', 'order.export',
            'product.view', 'product.create', 'product.update', 'product.link_channel',
            'inventory.view', 'tenant.view', 'tenant.member.view', 'shop.view'
        ]),
        ('WAREHOUSE', ARRAY[
            'order.view', 'order.package.ship', 'order.print', 'product.view',
            'inventory.view', 'inventory.adjust', 'inventory.transfer', 'inventory.stock_take',
            'tenant.view', 'shop.view'
        ]),
        ('ACCOUNTING', ARRAY[
            'order.view', 'order.export', 'product.view', 'inventory.view', 'tenant.view',
            'shop.view', 'finance.settlement.view', 'finance.settlement.export',
            'finance.reconciliation', 'audit_log.view', 'audit_log.export'
        ])
    ) AS defaults(builtin_key, permissions) ON defaults.builtin_key = r.builtin_key
    CROSS JOIN LATERAL unnest(defaults.permissions) AS p(permission_key)
    WHERE r.tenant_id = NEW.id;

    RETURN NEW;
END;
$$;

INSERT INTO role_permissions(role_id,permission_key)
SELECT r.id,p.key FROM roles r CROSS JOIN (VALUES ('billing.view'),('billing.manage')) p(key)
WHERE r.builtin_key='OWNER' ON CONFLICT DO NOTHING;
