# Conciliación segura de pagos · Soporte N3

Operación de soporte para PagoClaro que, dada la referencia externa de un pago, revisa el pago, los eventos del proveedor y los movimientos contables. Con esa evidencia decide si el pago está bien, si tiene una corrección segura o si necesita revisión manual.

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

Pon tus propias contraseñas en el `.env`, que no se sube al repositorio. La primera vez Oracle tarda unos 3 o 4 minutos; está listo cuando `docker compose ps` muestra `healthy`.

## Ejecutar

```bash
./mvnw spring-boot:run
```

En Windows: `.\mvnw.cmd spring-boot:run`. La aplicación toma el usuario y la contraseña del mismo `.env` que usa Docker Compose. Si existen variables de entorno con esos nombres, tienen prioridad sobre el archivo.

## Pruebas

```bash
./mvnw test
```

Las pruebas no necesitan Oracle. El reporte de cobertura queda en `target/site/jacoco/index.html`.
