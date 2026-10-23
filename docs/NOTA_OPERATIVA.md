# Nota operativa para soporte N3

## Antes de conciliar

1. Correr `db/03_detect_inconsistencies.sql` y ubicar el pago en la lista. La columna `finding` dice qué tiene: estado incompatible, débito neto distinto o evidencia contradictoria.
2. **Es seguro conciliar** cuando el proveedor solo reportó aprobaciones y todos los movimientos son por el monto del pago. En ese caso la operación solo hará una de dos cosas: confirmar el pago o reversar los débitos duplicados.
3. Si el hallazgo es `EVIDENCIA_CONTRADICTORIA`, la operación no va a cambiar nada. Igual se puede ejecutar para dejar el registro de revisión en la auditoría.

## Ejecutar

```bash
curl -X POST http://localhost:8080/api/support/payments/PAY-BX17/reconcile
```

Repetir la llamada no hace daño: si ya está corregido, responde `CONSISTENT` y no escribe nada.

## Cuándo detenerse

- **`NEEDS_REVIEW`:** no insistir ni corregir a mano desde la base de datos. Escalar con el motivo de la respuesta. Para CQ22, confirmar con el proveedor cuál fue el resultado real.
- **409:** otra conciliación tiene el pago ocupado. Esperar unos segundos y reintentar una sola vez. Si se repite, revisar si hay un proceso colgado sobre ese pago.
- **500:** no reintentar en bucle. Revisar el log del servicio buscando el `paymentRef`.
- **Un reverso en un pago donde el proveedor pudo haber cobrado dos veces (BX17):** el reverso corrige el ledger interno, pero la devolución al cliente se gestiona con el proveedor. No cerrar el caso sin esa confirmación.

## Qué revisar después

1. Volver a correr `db/03_detect_inconsistencies.sql`: el pago ya no debe aparecer, salvo que haya quedado en revisión manual.
2. En `LEDGER_ENTRY`, cada reverso debe tener `related_entry_id` apuntando al débito sobrante y el mismo monto. El débito neto debe ser igual al monto del pago.
3. En `SUPPORT_AUDIT`, una fila por acción (`PAYMENT_CONFIRMED`, `DEBIT_REVERSED` o `MANUAL_REVIEW`) y ninguna repetida.
4. En el log: `paymentRef=... outcome=... actions=[...]`.
