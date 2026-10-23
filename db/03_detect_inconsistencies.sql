-- Pagos con actividad en las últimas 24 horas cuyo débito neto no coincide con el esperado o cuyo estado no es compatible con el proveedor.
-- Uso: solo lectura; se ejecuta a mano (ver README). No lo carga el script de inicio.
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET LINESIZE 200 PAGESIZE 100
COLUMN external_ref FORMAT A14
COLUMN status FORMAT A10
COLUMN finding FORMAT A26

WITH recent_payments AS (
    SELECT p.id, p.external_ref, p.status, p.amount
    FROM payment p
    WHERE p.updated_at >= SYSTIMESTAMP - INTERVAL '24' HOUR
       OR EXISTS (SELECT 1 FROM provider_event e
                  WHERE e.payment_id = p.id AND e.received_at >= SYSTIMESTAMP - INTERVAL '24' HOUR)
       OR EXISTS (SELECT 1 FROM ledger_entry l
                  WHERE l.payment_id = p.id AND l.created_at >= SYSTIMESTAMP - INTERVAL '24' HOUR)
),
ledger_totals AS (
    SELECT payment_id,
           SUM(CASE entry_type WHEN 'DEBIT' THEN amount WHEN 'REVERSAL' THEN -amount END) AS net_debit
    FROM ledger_entry
    GROUP BY payment_id
),
provider_totals AS (
    SELECT payment_id,
           COUNT(CASE provider_status WHEN 'APPROVED' THEN 1 END) AS approved_events,
           COUNT(CASE provider_status WHEN 'DECLINED' THEN 1 END) AS declined_events
    FROM provider_event
    GROUP BY payment_id
),
evaluated AS (
    SELECT p.external_ref,
           p.status,
           p.amount,
           CASE p.status WHEN 'CONFIRMED' THEN p.amount ELSE 0 END AS expected_net_debit,
           NVL(l.net_debit, 0) AS net_debit,
           NVL(e.approved_events, 0) AS approved_events,
           NVL(e.declined_events, 0) AS declined_events
    FROM recent_payments p
    LEFT JOIN ledger_totals l ON l.payment_id = p.id
    LEFT JOIN provider_totals e ON e.payment_id = p.id
),
classified AS (
    SELECT evaluated.*,
           CASE
               WHEN status = 'CONFIRMED' AND approved_events > 0 AND declined_events = 0 THEN 'Y'
               WHEN status = 'REJECTED' AND declined_events > 0 AND approved_events = 0 THEN 'Y'
               WHEN status = 'PENDING' AND approved_events = 0 AND declined_events = 0 THEN 'Y'
               ELSE 'N'
           END AS status_matches_provider
    FROM evaluated
)
SELECT external_ref,
       status,
       amount,
       expected_net_debit,
       net_debit,
       approved_events,
       declined_events,
       CASE
           WHEN approved_events > 0 AND declined_events > 0 THEN 'EVIDENCIA_CONTRADICTORIA'
           WHEN status_matches_provider = 'N' THEN 'ESTADO_INCOMPATIBLE'
           ELSE 'DEBITO_NETO_DIFERENTE'
       END AS finding
FROM classified
WHERE net_debit <> expected_net_debit
   OR status_matches_provider = 'N'
ORDER BY external_ref;

EXIT
