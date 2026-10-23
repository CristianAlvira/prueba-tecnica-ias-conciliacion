# Uso de inteligencia artificial

Usé **Claude Code** (Claude, de Anthropic) como apoyo durante la prueba.

## Para qué la usé

1. **Generar pruebas unitarias:** las de la política de decisión, el caso de uso con Mockito y StepVerifier, la idempotencia (ejecutar dos veces y contar efectos), la API con `@WebFluxTest` y la detección del bloqueo de Oracle.
2. **Depurar y analizar errores bloqueantes más rápido,** porque el tiempo era limitado. Algunos ejemplos:
   - el `ORA-00054` que Spring envuelve en otra excepción;
   - la aplicación que no arrancaba por un bean de configuración sin registrar;
   - las consultas en paralelo sobre la misma conexión dentro de una transacción;
   - las tildes en los scripts SQL;
   - un reporte viejo de una prueba desechable.
3. **Acelerar la implementación** del código y los scripts, a partir del análisis inicial que hice del enunciado.
4. **Documentar:** redactar el README, el diagnóstico, las decisiones técnicas, la nota operativa y este archivo. Revisé los textos y pedí los ajustes necesarios para que dijeran lo que yo explicaría.

Todo con supervisión detallada:

- Antes de cada commit revisé el cambio en una pausa, y antes de cada merge revisé el pull request completo. Ningún commit se hizo sin mi aprobación.
- Revisé que la IA no cometiera errores de lógica, que no expusiera credenciales ni datos internos y que no introdujera cambios que rompieran el sistema o lo hicieran vulnerable. Por ejemplo: todos los valores van con `bind` en el SQL y las respuestas de error no muestran detalles internos.
- Cada cambio se verificó ejecutándolo: pruebas en verde antes de cada commit, `main` protegida y validación contra Oracle.

## Antes de iniciar el tiempo de la prueba

Hice un análisis inicial del enunciado con apoyo de la IA y preparé el entorno: descargué la imagen de Oracle Free y probé la conexión con un proyecto desechable que no forma parte del repositorio. No se escribió código de la solución antes de empezar. El repositorio y el primer commit son del inicio de la prueba (23/10, 4:33 p. m.).

## Durante la prueba

| Parte | En qué se usó la IA | Qué acepté, corregí o descarté | Cómo lo verifiqué |
|---|---|---|---|
| Entorno | `docker-compose.yml`, cargador de scripts, configuración de R2DBC, JaCoCo | Acepté. Descarté meter la aplicación en Docker Compose, porque el enunciado pide evitar infraestructura innecesaria. La IA agregó la importación opcional del `.env`, que acepté | `mvnw test` sin Oracle; contenedor `healthy`; conexión como el usuario de la app |
| Dominio | Modelo en records, decisiones selladas y la política | Antes de empezar recorté el alcance: un solo endpoint y solo dos correcciones automáticas; el pago pendiente rechazado va a revisión. Corregí un dato de prueba (`89000.00` por `89000`) que no aportaba | 12 pruebas de la política con los 4 casos y los bordes |
| Caso de uso | Puertos, transacción, auditoría única de revisión | Acepté. La IA cambió `Mono.zip` por lecturas en secuencia, porque dentro de la transacción todo comparte la conexión | Mockito con StepVerifier, y la prueba de ejecutar dos veces contando efectos |
| SQL y PL/SQL | Esquema, datos, consulta de 24 h, procedimiento, restricciones | Pedí una revisión de buenas prácticas y corregí 5 puntos: restricciones con nombre, `DELETE` limitados a los datos de muestra, sin `SELECT *`, validación solo sobre los casos de muestra y advertir que el procedimiento hace su propio `COMMIT` | Cada script ejecutado en Oracle y luego todo desde un volumen vacío |
| Persistencia | Adaptadores con `DatabaseClient`, traducción de `ORA-00054` a 409 | Acepté cambiar el orden del plan (persistencia antes que la API) para no dejar `main` sin arrancar entre pull requests. La IA agregó `@ConfigurationPropertiesScan` sin mostrármelo antes del commit; me lo informó y lo dejé | Prueba desechable contra Oracle, que no se commitea: los 4 casos, la referencia inexistente y un bloqueo retenido por otra conexión |
| API | Controlador, DTO, errores 400/404/409/500 | Acepté | `@WebFluxTest` y llamadas HTTP reales contra Oracle, incluidas dos simultáneas sobre BX17 |
| Documentación | Diagnóstico, decisiones, nota operativa, README y este archivo | Revisé los textos para que dijeran lo que yo explicaría | Lectura completa y comandos del README probados en un clon limpio |

## Qué rechacé o no dejé pasar

- Infraestructura extra: la aplicación en Docker, Testcontainers.
- Lombok: usé records de Java 21.
- Un segundo endpoint de solo diagnóstico.
- Correcciones automáticas que no pedían los datos: rechazar un pago pendiente o crear un débito faltante.
- Comentarios en el código Java: los nombres explican lo que hace cada parte.

## Secretos y datos sensibles

No compartí credenciales con la IA. Las contraseñas de la base local se generaron al azar en mi equipo, en un `.env` que no se sube al repositorio y que no se imprimió en ningún momento. El repositorio solo tiene `.env.example`.
