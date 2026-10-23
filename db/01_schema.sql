-- Tablas del enunciado y secuencias para los ids que crea la conciliación.
-- Uso: lo carga docker/initdb/01_load_db_scripts.sh al crear la base.
WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE TABLE payment (
    id           NUMBER CONSTRAINT pk_payment PRIMARY KEY,
    external_ref VARCHAR2(40) NOT NULL CONSTRAINT uk_payment_external_ref UNIQUE,
    status       VARCHAR2(20) NOT NULL,
    amount       NUMBER(18,2) NOT NULL,
    updated_at   TIMESTAMP NOT NULL,
    row_version  NUMBER DEFAULT 0 NOT NULL
);

CREATE TABLE provider_event (
    id                NUMBER CONSTRAINT pk_provider_event PRIMARY KEY,
    payment_id        NUMBER NOT NULL CONSTRAINT fk_provider_event_payment REFERENCES payment(id),
    provider_event_id VARCHAR2(60) NOT NULL CONSTRAINT uk_provider_event_external_id UNIQUE,
    provider_status   VARCHAR2(20) NOT NULL,
    received_at       TIMESTAMP NOT NULL
);

CREATE TABLE ledger_entry (
    id               NUMBER CONSTRAINT pk_ledger_entry PRIMARY KEY,
    payment_id       NUMBER NOT NULL CONSTRAINT fk_ledger_entry_payment REFERENCES payment(id),
    entry_type       VARCHAR2(20) NOT NULL,
    amount           NUMBER(18,2) NOT NULL,
    related_entry_id NUMBER NULL CONSTRAINT fk_ledger_entry_related REFERENCES ledger_entry(id),
    created_at       TIMESTAMP NOT NULL
);

CREATE TABLE support_audit (
    id         NUMBER CONSTRAINT pk_support_audit PRIMARY KEY,
    payment_id NUMBER NOT NULL CONSTRAINT fk_support_audit_payment REFERENCES payment(id),
    action     VARCHAR2(40) NOT NULL,
    reason     VARCHAR2(400) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE SEQUENCE ledger_entry_seq START WITH 9001;
CREATE SEQUENCE support_audit_seq START WITH 1;

EXIT
