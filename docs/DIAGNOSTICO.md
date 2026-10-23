# Diagnóstico del incidente PAY-BX17

## Hechos observados

- 09:41:22.104: se llamó al proveedor para aprobar el pago (`attempt=1`).
- 09:41:23.983: el cliente dejó de esperar a los 1800 ms (`timeout`). Eso no significa que el proveedor haya fallado, solo que no respondió a tiempo.
- 09:41:24.106: se programó un reintento por ese timeout.
- 09:41:25.317: llegó el evento `EVT-B2` con estado `APPROVED`. Que se llame `B2` sugiere que existió un `EVT-B1`, que no aparece en el extracto. Los datos de muestra confirman dos eventos `APPROVED` para BX17.
- 09:41:25.390: se creó el débito 5003.
- 09:41:25.611: el reintento encontró el pago ya `CONFIRMED`. Esta verificación ocurrió **221 ms después** de crear el débito.
- 09:43:08.512: el job de conciliación encontró un débito neto de 178000 cuando lo esperado era 89000. Hay dos débitos por el monto completo y ningún reverso.

El extracto es parcial: no muestra el primer débito ni el procesamiento de `EVT-B1`.

## Hipótesis de causa

1. **El primer intento sí llegó al proveedor.** El proveedor aprobó (`EVT-B1`), el sistema creó el primer débito y confirmó el pago. El reintento generó una segunda aprobación (`EVT-B2`), y su manejador creó el débito 5003 antes de comprobar que el pago ya estaba confirmado. El orden del log (débito creado y después "already CONFIRMED") apoya esta hipótesis.
2. **Carrera entre los dos eventos:** ambos leyeron el pago como pendiente casi al mismo tiempo y cada uno creó su débito.
3. **La deduplicación existente es por `provider_event_id`.** Como `EVT-B1` y `EVT-B2` son ids distintos, la restricción `UNIQUE` no los detuvo. La protección correcta es por pago: no crear un débito si ya existe uno vigente.

Es un **hecho** que hay dos débitos y ningún reverso. Es una **hipótesis** que la causa sea el reintento sin verificación previa: no tenemos el código del flujo de pagos ni el log del primer débito.

## Validaciones que haría en producción antes de corregir

1. Confirmar con el proveedor (panel o reporte de liquidación) si cobró una o dos veces. Si cobró dos veces, además del reverso interno hay que gestionar la devolución con el proveedor.
2. Buscar en los logs el procesamiento de `EVT-B1` y la creación del primer débito, para confirmar el orden.
3. Correr `db/03_detect_inconsistencies.sql` para saber cuántos pagos más están afectados y de qué tipo.
4. Revisar el manejador de eventos del proveedor: si bloquea el pago y si verifica el débito vigente antes de insertar.
5. Probar la conciliación primero sobre una copia de los datos.

## Corrección propuesta

- **Para el incidente:** la operación de conciliación de este repositorio. Para BX17 crea un único `REVERSAL` del débito más reciente (5003), relacionado con él, y deja el original. El estado del pago no cambia, y una segunda ejecución ya no encuentra nada que corregir.
- **Para la causa raíz (fuera del alcance de las 2 horas):** que el manejador de eventos bloquee el pago y verifique el débito vigente antes de insertar, y dejar en la base el índice único de `db/05_optional_constraints.sql` para que un débito no se pueda reversar dos veces.
