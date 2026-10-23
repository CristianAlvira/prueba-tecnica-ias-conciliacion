-- Los cuatro casos de muestra del enunciado, con fechas relativas a SYSTIMESTAMP para que entren en la ventana de 24 horas.
-- Uso: lo carga el script de inicio; se puede volver a ejecutar para reiniciar los casos (solo borra los pagos 1 a 4 y lo que depende de ellos).
WHENEVER SQLERROR EXIT SQL.SQLCODE

DELETE FROM support_audit WHERE payment_id IN (1, 2, 3, 4);
DELETE FROM ledger_entry WHERE payment_id IN (1, 2, 3, 4) AND related_entry_id IS NOT NULL;
DELETE FROM ledger_entry WHERE payment_id IN (1, 2, 3, 4);
DELETE FROM provider_event WHERE payment_id IN (1, 2, 3, 4);
DELETE FROM payment WHERE id IN (1, 2, 3, 4);

INSERT INTO payment (id, external_ref, status, amount, updated_at) VALUES (1, 'PAY-AX91', 'PENDING',   125000, SYSTIMESTAMP - INTERVAL '3' HOUR);
INSERT INTO payment (id, external_ref, status, amount, updated_at) VALUES (2, 'PAY-BX17', 'CONFIRMED',  89000, SYSTIMESTAMP - INTERVAL '2' HOUR);
INSERT INTO payment (id, external_ref, status, amount, updated_at) VALUES (3, 'PAY-CQ22', 'PENDING',    54000, SYSTIMESTAMP - INTERVAL '90' MINUTE);
INSERT INTO payment (id, external_ref, status, amount, updated_at) VALUES (4, 'PAY-DK44', 'CONFIRMED',  31000, SYSTIMESTAMP - INTERVAL '1' HOUR);

INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES (1, 1, 'EVT-A1', 'APPROVED', SYSTIMESTAMP - INTERVAL '3' HOUR);
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES (2, 2, 'EVT-B1', 'APPROVED', SYSTIMESTAMP - INTERVAL '2' HOUR);
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES (3, 2, 'EVT-B2', 'APPROVED', SYSTIMESTAMP - INTERVAL '2' HOUR + INTERVAL '3' SECOND);
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES (4, 3, 'EVT-C1', 'APPROVED', SYSTIMESTAMP - INTERVAL '90' MINUTE);
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES (5, 3, 'EVT-C2', 'DECLINED', SYSTIMESTAMP - INTERVAL '89' MINUTE);
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES (6, 4, 'EVT-D1', 'APPROVED', SYSTIMESTAMP - INTERVAL '1' HOUR);

INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES (5001, 1, 'DEBIT', 125000, NULL, SYSTIMESTAMP - INTERVAL '3' HOUR);
INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES (5002, 2, 'DEBIT',  89000, NULL, SYSTIMESTAMP - INTERVAL '2' HOUR);
INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES (5003, 2, 'DEBIT',  89000, NULL, SYSTIMESTAMP - INTERVAL '2' HOUR + INTERVAL '3' SECOND);
INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES (5004, 4, 'DEBIT',  31000, NULL, SYSTIMESTAMP - INTERVAL '1' HOUR);

COMMIT;

EXIT
