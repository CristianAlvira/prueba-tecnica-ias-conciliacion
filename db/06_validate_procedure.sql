-- Validación del procedimiento: concilia cada pago de muestra dos veces y muestra el resultado y los conteos finales.
-- Uso: modifica datos; ejecutar después de 02_sample_data.sql y volver a cargar ese script para reiniciar.
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET SERVEROUTPUT ON
SET LINESIZE 200 PAGESIZE 100
COLUMN external_ref FORMAT A14
COLUMN status FORMAT A10

DECLARE
    c_attempts CONSTANT PLS_INTEGER := 2;
    v_outcome  VARCHAR2(20);
    v_reason   VARCHAR2(400);
BEGIN
    FOR payment_row IN (SELECT external_ref FROM payment ORDER BY id) LOOP
        FOR attempt IN 1 .. c_attempts LOOP
            reconcile_payment(payment_row.external_ref, v_outcome, v_reason);
            DBMS_OUTPUT.PUT_LINE(payment_row.external_ref || ' intento ' || attempt || ': ' || v_outcome
                                 || CASE WHEN v_reason IS NOT NULL THEN ' (' || v_reason || ')' END);
        END LOOP;
    END LOOP;
END;
/

SELECT p.external_ref,
       p.status,
       (SELECT COUNT(*) FROM ledger_entry l WHERE l.payment_id = p.id AND l.entry_type = 'REVERSAL') AS reversals,
       (SELECT NVL(SUM(CASE l.entry_type WHEN 'DEBIT' THEN l.amount ELSE -l.amount END), 0)
        FROM ledger_entry l WHERE l.payment_id = p.id) AS net_debit,
       (SELECT COUNT(*) FROM support_audit a WHERE a.payment_id = p.id) AS audits
FROM payment p
ORDER BY p.id;

EXIT
