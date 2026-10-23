-- Procedimiento reconcile_payment: concilia un pago por referencia externa con las mismas reglas que la API, en una sola transacción.
-- Uso: EXEC reconcile_payment('PAY-BX17', :outcome, :reason); controla su propia transacción (COMMIT o ROLLBACK), no llamarlo dentro de otra.
WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE OR REPLACE PROCEDURE reconcile_payment (
    p_external_ref IN  payment.external_ref%TYPE,
    p_outcome      OUT VARCHAR2,
    p_reason       OUT VARCHAR2
) AS
    c_debits_to_keep          CONSTANT PLS_INTEGER := 1;
    c_reason_contradictory    CONSTANT VARCHAR2(400) := 'El proveedor reportó aprobación y rechazo para el mismo pago';
    c_reason_no_evidence      CONSTANT VARCHAR2(400) := 'No hay eventos del proveedor para decidir';
    c_reason_declined         CONSTANT VARCHAR2(400) := 'El proveedor rechazó el pago y el estado o el ledger no coinciden con ese rechazo';
    c_reason_status_rejected  CONSTANT VARCHAR2(400) := 'El pago está rechazado, pero el proveedor lo aprobó';
    c_reason_unexpected       CONSTANT VARCHAR2(400) := 'Los movimientos contables no coinciden con el monto del pago o tienen reversos inválidos';
    c_reason_missing_debit    CONSTANT VARCHAR2(400) := 'El proveedor aprobó el pago, pero no existe un débito vigente';
    c_audit_confirmed         CONSTANT VARCHAR2(400) := 'Pago confirmado: el proveedor lo aprobó y tiene su débito';
    c_audit_reversed          CONSTANT VARCHAR2(400) := 'Reverso de un débito duplicado del pago';

    v_payment       payment%ROWTYPE;
    v_approved      PLS_INTEGER;
    v_declined      PLS_INTEGER;
    v_invalid_rows  PLS_INTEGER;
    v_active_debits PLS_INTEGER;
    v_changes       PLS_INTEGER := 0;

    PROCEDURE audit(p_action IN VARCHAR2, p_text IN VARCHAR2) IS
    BEGIN
        INSERT INTO support_audit (id, payment_id, action, reason, created_at)
        VALUES (support_audit_seq.NEXTVAL, v_payment.id, p_action, p_text, SYSTIMESTAMP);
    END;

    PROCEDURE request_review(p_text IN VARCHAR2) IS
        v_already_audited PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_already_audited
        FROM support_audit
        WHERE payment_id = v_payment.id AND action = 'MANUAL_REVIEW' AND reason = p_text;

        IF v_already_audited = 0 THEN
            audit('MANUAL_REVIEW', p_text);
        END IF;
        p_outcome := 'NEEDS_REVIEW';
        p_reason := p_text;
    END;

    PROCEDURE apply_safe_corrections IS
    BEGIN
        IF v_payment.status = 'PENDING' THEN
            UPDATE payment
            SET status = 'CONFIRMED', updated_at = SYSTIMESTAMP, row_version = row_version + 1
            WHERE id = v_payment.id;
            audit('PAYMENT_CONFIRMED', c_audit_confirmed);
            v_changes := v_changes + 1;
        END IF;

        FOR duplicate IN (
            SELECT d.id, d.amount
            FROM ledger_entry d
            WHERE d.payment_id = v_payment.id
              AND d.entry_type = 'DEBIT'
              AND NOT EXISTS (SELECT 1 FROM ledger_entry r WHERE r.related_entry_id = d.id)
            ORDER BY d.created_at, d.id
            OFFSET c_debits_to_keep ROWS
        ) LOOP
            INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at)
            VALUES (ledger_entry_seq.NEXTVAL, v_payment.id, 'REVERSAL', duplicate.amount, duplicate.id, SYSTIMESTAMP);
            audit('DEBIT_REVERSED', c_audit_reversed);
            v_changes := v_changes + 1;
        END LOOP;

        p_outcome := CASE WHEN v_changes > 0 THEN 'CORRECTED' ELSE 'CONSISTENT' END;
        p_reason := NULL;
    END;
BEGIN
    SELECT * INTO v_payment
    FROM payment
    WHERE external_ref = p_external_ref
    FOR UPDATE WAIT 5;

    SELECT COUNT(CASE provider_status WHEN 'APPROVED' THEN 1 END),
           COUNT(CASE provider_status WHEN 'DECLINED' THEN 1 END)
    INTO v_approved, v_declined
    FROM provider_event
    WHERE payment_id = v_payment.id;

    SELECT COUNT(*) INTO v_invalid_rows
    FROM ledger_entry l
    WHERE l.payment_id = v_payment.id
      AND (l.amount <> v_payment.amount
           OR l.entry_type NOT IN ('DEBIT', 'REVERSAL')
           OR (l.entry_type = 'REVERSAL'
               AND (NOT EXISTS (SELECT 1 FROM ledger_entry d
                                WHERE d.id = l.related_entry_id
                                  AND d.payment_id = v_payment.id
                                  AND d.entry_type = 'DEBIT')
                    OR (SELECT COUNT(*) FROM ledger_entry r WHERE r.related_entry_id = l.related_entry_id) > 1)));

    SELECT COUNT(*) INTO v_active_debits
    FROM ledger_entry d
    WHERE d.payment_id = v_payment.id
      AND d.entry_type = 'DEBIT'
      AND NOT EXISTS (SELECT 1 FROM ledger_entry r WHERE r.related_entry_id = d.id);

    IF v_approved > 0 AND v_declined > 0 THEN
        request_review(c_reason_contradictory);
    ELSIF v_approved > 0 THEN
        IF v_payment.status = 'REJECTED' THEN
            request_review(c_reason_status_rejected);
        ELSIF v_invalid_rows > 0 THEN
            request_review(c_reason_unexpected);
        ELSIF v_active_debits = 0 THEN
            request_review(c_reason_missing_debit);
        ELSE
            apply_safe_corrections;
        END IF;
    ELSIF v_declined > 0 THEN
        IF v_payment.status = 'REJECTED' AND v_active_debits = 0 THEN
            p_outcome := 'CONSISTENT';
            p_reason := NULL;
        ELSE
            request_review(c_reason_declined);
        END IF;
    ELSE
        request_review(c_reason_no_evidence);
    END IF;

    COMMIT;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        ROLLBACK;
        RAISE_APPLICATION_ERROR(-20001, 'No existe un pago con la referencia ' || p_external_ref);
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END reconcile_payment;
/

SHOW ERRORS PROCEDURE reconcile_payment

EXIT
