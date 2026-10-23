# Decisiones técnicas y supuestos

## 1. La decisión vive en Java, como función pura

- **Contexto:** es la parte más importante y la que más se debe probar.
- **Alternativas:** toda la lógica en PL/SQL con Java solo llamando al procedimiento; o Java diagnostica y el procedimiento aplica.
- **Decisión:** `ReconciliationPolicy` recibe la evidencia (pago, eventos y ledger) y devuelve la decisión, sin efectos. El procedimiento `reconcile_payment` aplica la misma tabla para quien trabaja directo en la base.
- **Trade-off:** la regla está en dos lugares.
- **Riesgo:** que una implementación cambie y la otra no. Se mitiga con la misma tabla documentada, los mismos textos de auditoría y los mismos casos de prueba.
- **Verificación:** 12 pruebas de la política, y `db/06_validate_procedure.sql` con el mismo resultado para los 4 casos.

## 2. Tabla de decisión

| Evidencia | Estado del pago | Resultado |
|---|---|---|
| Aprobado y rechazado a la vez | Cualquiera | Revisión manual: evidencia contradictoria (CQ22) |
| Sin eventos | Cualquiera | Revisión manual |
| Solo aprobado | `REJECTED` | Revisión manual |
| Solo aprobado, con algún movimiento por otro monto o un reverso inválido | Cualquiera | Revisión manual |
| Solo aprobado, sin débito vigente | `PENDING` o `CONFIRMED` | Revisión manual (no se crean débitos) |
| Solo aprobado, un débito vigente | `CONFIRMED` | Consistente (DK44) |
| Solo aprobado, un débito vigente | `PENDING` | Se confirma el pago (AX91) |
| Solo aprobado, varios débitos vigentes | `PENDING` o `CONFIRMED` | Se reversan todos menos el más antiguo y, si estaba pendiente, se confirma (BX17) |
| Solo rechazado, sin débito vigente | `REJECTED` | Consistente |
| Solo rechazado | Otro caso | Revisión manual |

"Débito vigente" es un débito que no tiene un reverso relacionado.

## 3. Cuándo se modifican datos

La API y el procedimiento solo escriben en tres casos:

1. **Confirmar el pago:** `UPDATE payment` a `CONFIRMED`, con `updated_at` y `row_version + 1`. Solo si está `PENDING`, el proveedor lo aprobó sin rechazos y tiene al menos un débito vigente, con todos los movimientos por el monto del pago.
2. **Reversar duplicados:** `INSERT` de un `REVERSAL` por cada débito vigente sobrante, con `related_entry_id` apuntando al débito y el mismo monto. Nunca se modifica ni se borra un movimiento.
3. **Auditar:** una fila en `SUPPORT_AUDIT` por cada acción anterior (`PAYMENT_CONFIRMED`, `DEBIT_REVERSED`), y una por revisión manual (`MANUAL_REVIEW`) solo si no existe ya una con el mismo motivo.

Todo ocurre en una sola transacción: si algo falla, no queda nada a medias. El procedimiento hace su propio `COMMIT` o `ROLLBACK`, así que no se debe llamar dentro de otra transacción.

## 4. Concurrencia

- **Contexto:** dos solicitudes casi simultáneas sobre el mismo pago.
- **Alternativas:** bloqueo optimista con `row_version` (detectar el conflicto y reintentar), o bloqueo pesimista.
- **Decisión:** pesimista, con `SELECT ... FOR UPDATE WAIT 5` sobre `PAYMENT` al inicio de la transacción. La segunda solicitud espera, luego ve el estado ya corregido y no hace nada. Si espera más de 5 segundos (`ORA-00054`), responde 409 y se puede reintentar. Como red de seguridad, el índice único de `related_entry_id` impide reversar dos veces el mismo débito.
- **Trade-off:** las solicitudes sobre el mismo pago se atienden una por una. Para un proceso de soporte es aceptable.
- **Verificación:** dos llamadas simultáneas a BX17 dieron una `CORRECTED` y otra `CONSISTENT`. Con otra conexión reteniendo el bloqueo, la API respondió `PaymentLockedException` (409). Un segundo reverso del mismo débito falla con `ORA-00001`.

## 5. Idempotencia

- **Decisión:** la operación decide siempre con el estado actual, no con lo que se pidió. Lo que ya se corrigió deja de ser una corrección pendiente. La auditoría de revisión no se repite porque se busca una igual antes de insertar.
- **Verificación:** `ReconcileTwiceIdempotencyTest` ejecuta cada caso dos veces y cuenta los efectos. Lo mismo se comprobó contra Oracle con la API y con el procedimiento.

## 6. Acceso a datos reactivo con SQL explícito

- **Decisión:** WebFlux con R2DBC (`DatabaseClient`) y SQL escrito a mano, con arquitectura de puertos y adaptadores. El dominio no conoce Spring ni Oracle.
- **Alternativa:** Spring Data con entidades, o JDBC con `@Transactional`.
- **Por qué:** el `FOR UPDATE WAIT`, la secuencia y cada sentencia quedan visibles y fáciles de revisar.
- **Riesgo:** más código manual de mapeo. Es poco, porque son cuatro tablas.

## 7. Pruebas

- Política pura, sin mocks.
- Caso de uso con Mockito y StepVerifier.
- Idempotencia con una base de datos en memoria.
- Controlador con `@WebFluxTest`.
- Detección del error de bloqueo de Oracle.

Ninguna necesita Oracle: el evaluador puede correr `./mvnw test` sin Docker. La parte de Oracle se valida con los scripts y las llamadas descritas en el README. No se usó Testcontainers porque la imagen pesa casi 3 GB y haría las pruebas muy lentas.

## 8. Cambios opcionales al esquema (`db/05_optional_constraints.sql`)

| Cambio | Propósito |
|---|---|
| Índice único en `ledger_entry(related_entry_id)` | Un débito solo se puede reversar una vez, aunque fallara el bloqueo |
| Índices en `payment_id` de las tres tablas hijas | Leer la evidencia de un pago sin recorrer la tabla completa |
| `CHECK` de estados y tipos | Solo se aceptan los valores del enunciado |
| `CHECK` de `related_entry_id` | Un débito nunca tiene movimiento relacionado y un reverso siempre lo tiene |

## 9. Supuestos

| # | Vacío del enunciado | Supuesto | Riesgo | Qué comprobaría en producción |
|---|---|---|---|---|
| S1 | Cómo se generan los ids | Secuencias `ledger_entry_seq` y `support_audit_seq` | Bajo | El mecanismo real del esquema |
| S2 | Cuál débito de BX17 es el duplicado | El más reciente (por fecha y, si empatan, por id) | Nulo en lo contable | — |
| S3 | Si el proveedor cobró una o dos veces | Solo se corrige el ledger interno | Alto si cobró dos veces | Reporte de liquidación de `EVT-B1` y `EVT-B2` |
| S4 | Montos de los movimientos de muestra | Iguales al monto del pago. Si no, revisión manual | Bajo | — |
| S5 | Pago aprobado sin débito | No se crea un débito automáticamente | Medio: deja casos sin resolver | Confirmación del proveedor antes de crearlo a mano |
| S6 | "Últimas 24 horas" respecto a qué | Actividad del pago: `updated_at`, un evento o un movimiento en la ventana | Bajo | Acordarlo con operaciones |
| S7 | Rechazo después de una aprobación (CQ22) | Se trata como contradictorio | Bajo | Semántica de eventos del proveedor |
| S8 | Pago pendiente rechazado por el proveedor | No se cambia a `REJECTED`; va a revisión | Bajo: es la opción conservadora | Si operaciones quiere automatizarlo |
| S9 | Valores de `SUPPORT_AUDIT.action` | `PAYMENT_CONFIRMED`, `DEBIT_REVERSED`, `MANUAL_REVIEW` | Bajo | Catálogo real |
| S10 | Auditoría del caso sano | No se audita; queda solo en el log | Bajo | — |
| S11 | Moneda y zona horaria | Una sola moneda, y la hora de la base (`SYSTIMESTAMP`) | Bajo | — |
| S12 | Autenticación del endpoint | Fuera de alcance | Medio | Exponerlo solo detrás de seguridad y para N3 |

## 10. Consulta de detección (`db/03_detect_inconsistencies.sql`)

La consulta está armada por pasos con `WITH`, para que cada condición tenga nombre y se pueda revisar sola:

| Paso | Qué calcula | Por qué |
|---|---|---|
| `recent_payments` | Pagos con actividad en las últimas 24 horas: cambió el pago, llegó un evento o se creó un movimiento | Si solo se mirara `updated_at`, se escaparía un pago viejo que acaba de recibir un evento o un movimiento (supuesto S6) |
| `ledger_totals` | Débito neto: suma de `DEBIT` menos suma de `REVERSAL` | Es la regla del enunciado: un pago confirmado termina con un único débito neto por su valor |
| `provider_totals` | Cuántos eventos aprobados y rechazados tiene cada pago | Para comparar el estado interno con lo que dijo el proveedor |
| `evaluated` | Débito neto esperado: el monto si el pago está `CONFIRMED`, y 0 en cualquier otro estado | Un pago pendiente o rechazado no debería tener dinero debitado |
| `classified` | Si el estado es compatible con el proveedor: `CONFIRMED` solo con aprobaciones, `REJECTED` solo con rechazos, `PENDING` sin eventos | Cualquier otra combinación es una inconsistencia |
| Resultado | Pagos con débito neto distinto al esperado **o** estado incompatible, con la columna `finding` | `EVIDENCIA_CONTRADICTORIA` se muestra primero porque es el caso que no se puede corregir solo |

Se usa `LEFT JOIN` con `NVL(..., 0)` para que un pago sin movimientos o sin eventos no desaparezca del resultado. La consulta es de solo lectura y no la ejecuta el script de inicio. Con los datos de muestra devuelve PAY-AX91, PAY-BX17 y PAY-CQ22, y no devuelve PAY-DK44.
