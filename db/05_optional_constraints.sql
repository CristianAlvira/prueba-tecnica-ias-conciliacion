-- Cambios opcionales al esquema: un movimiento solo se puede reversar una vez, índices por pago y valores permitidos.
-- Uso: lo carga el script de inicio después de los datos; el propósito de cada cambio está en docs/DECISIONES.md.
WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE UNIQUE INDEX ux_ledger_entry_related ON ledger_entry (related_entry_id);

CREATE INDEX ix_provider_event_payment ON provider_event (payment_id);
CREATE INDEX ix_ledger_entry_payment ON ledger_entry (payment_id);
CREATE INDEX ix_support_audit_payment ON support_audit (payment_id);

ALTER TABLE payment ADD CONSTRAINT ck_payment_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'));
ALTER TABLE provider_event ADD CONSTRAINT ck_provider_event_status
    CHECK (provider_status IN ('APPROVED', 'DECLINED'));
ALTER TABLE ledger_entry ADD CONSTRAINT ck_ledger_entry_type
    CHECK (entry_type IN ('DEBIT', 'REVERSAL'));
ALTER TABLE ledger_entry ADD CONSTRAINT ck_ledger_entry_related
    CHECK ((entry_type = 'DEBIT' AND related_entry_id IS NULL)
        OR (entry_type = 'REVERSAL' AND related_entry_id IS NOT NULL));

EXIT
