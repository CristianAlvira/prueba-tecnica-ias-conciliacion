# Conciliación segura de pagos · Soporte N3

Operación de soporte para PagoClaro que, dada la referencia externa de un pago, revisa el pago, los eventos del proveedor y los movimientos contables. Con esa evidencia decide si el pago está bien, si tiene una corrección segura o si necesita revisión manual. Solo aplica lo seguro, en una transacción, sin duplicar efectos aunque se repita y sin romperse si llegan dos solicitudes al tiempo.

Stack: Java 21, Spring Boot 4.1.2 (WebFlux y R2DBC) y Oracle Database Free en Docker.

## Requisitos

- Java 21
- Docker Desktop

No hace falta instalar Maven: el proyecto trae el Maven Wrapper (`mvnw`).

## Levantar Oracle

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

En Windows el primer comando es `copy .env.example .env`. Pon tus propias contraseñas en el `.env`, que no se sube al repositorio. La primera vez Oracle tarda unos 3 o 4 minutos; está listo cuando `docker compose ps` muestra `healthy`.

Al crear la base, `docker/initdb/01_load_db_scripts.sh` carga como el usuario de la aplicación, en `FREEPDB1`, estos scripts: el esquema, los cuatro casos de muestra, el procedimiento PL/SQL y las restricciones opcionales.

## Ejecutar

```bash
./mvnw spring-boot:run
```

En Windows: `.\mvnw.cmd spring-boot:run`. La aplicación toma el usuario y la contraseña del mismo `.env` que usa Docker Compose. Si existen variables de entorno con esos nombres, tienen prioridad sobre el archivo.

## Conciliar un pago

```bash
curl -X POST http://localhost:8080/api/support/payments/PAY-BX17/reconcile
```

Resultados con los datos de muestra:

| Referencia | Primera llamada | Segunda llamada |
|---|---|---|
| `PAY-AX91` | `CORRECTED`, `["Pago confirmado"]` | `CONSISTENT` |
| `PAY-BX17` | `CORRECTED`, `["Reverso del débito 5003"]` | `CONSISTENT` |
| `PAY-CQ22` | `NEEDS_REVIEW`, motivo: evidencia contradictoria | `NEEDS_REVIEW` (sin nueva auditoría) |
| `PAY-DK44` | `CONSISTENT` | `CONSISTENT` |

Ejemplos de respuesta:

```json
{"externalRef":"PAY-BX17","outcome":"CORRECTED","actions":["Reverso del débito 5003"]}
{"externalRef":"PAY-CQ22","outcome":"NEEDS_REVIEW","actions":[],"reason":"El proveedor reportó aprobación y rechazo para el mismo pago"}
```

Errores:

| Código | Cuándo |
|---|---|
| 404 | La referencia no existe |
| 409 | Otra conciliación tiene el pago bloqueado; se puede reintentar en unos segundos |
| 400 | La referencia supera los 40 caracteres |
| 500 | Error inesperado; la respuesta no trae detalles internos, que quedan en el log |

## Pruebas

```bash
./mvnw test
```

Son 30 pruebas y ninguna necesita Oracle. Cubren:

- la política de decisión: los 4 casos de muestra y 7 bordes;
- el caso de uso, con Mockito;
- **la idempotencia**: `ReconcileTwiceIdempotencyTest` ejecuta cada caso dos veces y comprueba que hay un solo reverso, una sola confirmación y una sola auditoría;
- la API: 200, 400, 404, 409 y 500;
- la detección del bloqueo ocupado de Oracle.

El reporte de cobertura queda en `target/site/jacoco/index.html`.

## Scripts de base de datos (`db/`)

| Script | Qué hace | Se carga al iniciar |
|---|---|---|
| `01_schema.sql` | Tablas del enunciado y secuencias | Sí |
| `02_sample_data.sql` | Los 4 casos con fechas relativas a la hora actual. Se puede volver a ejecutar para reiniciarlos | Sí |
| `03_detect_inconsistencies.sql` | Consulta de las últimas 24 horas: débito neto distinto al esperado o estado incompatible con el proveedor | No (solo lectura) |
| `04_reconcile_payment.sql` | Procedimiento `reconcile_payment(p_external_ref IN, p_outcome OUT, p_reason OUT)` | Sí |
| `05_optional_constraints.sql` | Índice único de reversos, índices por pago y `CHECK` de valores | Sí |
| `06_validate_procedure.sql` | Concilia los 4 casos dos veces con el procedimiento y muestra los conteos | No (modifica datos) |

Para ejecutar uno a mano:

```bash
docker exec pagoclaro-oracle bash -c 'sqlplus -s -L "$APP_USER/$APP_USER_PASSWORD@//localhost/FREEPDB1" @/opt/db/03_detect_inconsistencies.sql'
```

Salida de la consulta con los datos de muestra:

```
EXTERNAL_REF  STATUS     AMOUNT  EXPECTED_NET_DEBIT  NET_DEBIT  APPROVED  DECLINED  FINDING
PAY-AX91      PENDING    125000                   0     125000         1         0  ESTADO_INCOMPATIBLE
PAY-BX17      CONFIRMED   89000               89000     178000         2         0  DEBITO_NETO_DIFERENTE
PAY-CQ22      PENDING     54000                   0          0         1         1  EVIDENCIA_CONTRADICTORIA
```

Para reiniciar los datos de muestra, usa el mismo comando con `02_sample_data.sql`. Para empezar desde cero, `docker compose down -v` y `docker compose up -d`.

## Validación contra Oracle

Probado con Oracle Free en Docker, desde un volumen vacío:

- los 4 casos llamados dos veces por la API dan la tabla de arriba;
- `06_validate_procedure.sql` da el mismo resultado con el procedimiento (un solo reverso en BX17, del débito 5003);
- dos llamadas simultáneas a BX17 responden una `CORRECTED` y otra `CONSISTENT`;
- con otra conexión reteniendo el bloqueo del pago, la API responde 409;
- un segundo reverso del mismo débito falla con `ORA-00001` por el índice único.

## Documentación

- [Diagnóstico del incidente](docs/DIAGNOSTICO.md): hechos, hipótesis, validaciones y corrección.
- [Decisiones técnicas y supuestos](docs/DECISIONES.md): tabla de decisión, cuándo se modifican datos, concurrencia, idempotencia y supuestos con su riesgo.
- [Nota operativa para soporte](docs/NOTA_OPERATIVA.md): cuándo es seguro conciliar, cuándo detenerse y qué revisar después.
- [Uso de IA](USO_DE_IA.md)

## Pendiente y siguientes pasos

- **Causa raíz:** que el manejador de eventos del proveedor bloquee el pago y verifique el débito vigente antes de insertar. Está fuera del alcance de esta corrección.
- **Seguridad:** el endpoint no tiene autenticación. En producción iría detrás de la seguridad del servicio y solo para soporte N3.
- **Textos compartidos:** los motivos de auditoría están en Java y en el PL/SQL. Si se cambia uno, hay que cambiar el otro, o llevarlos a una tabla de catálogo.
- **Pruebas de integración automáticas con Oracle** (por ejemplo con Testcontainers), si el equipo acepta el tiempo de arranque de la imagen.
- **Contenerizar la aplicación** con un Dockerfile multi-stage, como otro servicio del compose.

## Estructura

```
src/main/java/com/pagoclaro/reconciliation
├── domain            modelo, puertos, política de decisión y caso de uso (sin Spring)
└── infrastructure    controlador, DTO, mapper, manejo de errores, adaptadores R2DBC y configuración
db/                   scripts SQL y PL/SQL
docker/initdb/        script que carga db/ al crear la base
docs/                 diagnóstico, decisiones y nota operativa
```
