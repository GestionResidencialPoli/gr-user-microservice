# GR User Microservice

Microservicio de usuarios y autenticación de la plataforma de Gestión Residencial. Se encarga del registro, autenticación y administración de las cuentas de los residentes y administradores de las unidades residenciales.

## Stack técnico

- Java 17
- Spring Boot 4.1.1
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- Maven

## Requisitos previos

- JDK 17
- PostgreSQL 14+ corriendo en local
- Maven (o usar el wrapper `./mvnw` incluido en el repo)

## Configuración

El servicio se conecta a PostgreSQL mediante variables de entorno, con valores por defecto pensados para un ambiente local. Las variables disponibles están documentadas en [`.env.example`](.env.example):

| Variable      | Descripción                       | Valor por defecto                             |
|---------------|------------------------------------|------------------------------------------------|
| `DB_URL`      | URL JDBC de conexión a PostgreSQL  | `jdbc:postgresql://localhost:5432/gr_user_db` |
| `DB_USERNAME` | Usuario de la base de datos        | `postgres`                                     |
| `DB_PASSWORD` | Contraseña de la base de datos     | `postgres`                                     |
| `JPA_SHOW_SQL` | Muestra las sentencias SQL de JPA | `false`                                        |

Antes de levantar el servicio, crea la base de datos en tu PostgreSQL local:

```sql
CREATE DATABASE gr_user_db;
```

Flyway crea y evoluciona el esquema automáticamente al arrancar. Hibernate está configurado con `ddl-auto=validate`: valida las entidades, pero nunca crea ni modifica tablas.

## Cómo ejecutar en local

```bash
./mvnw spring-boot:run
```

El servicio queda disponible en `http://localhost:8080`.

## Cómo ejecutar las pruebas

```bash
./mvnw test
```

Las pruebas de integración usan PostgreSQL 16 mediante Testcontainers, por lo que requieren un motor Docker activo.

## Modelo de identidad

El modelo entidad-relación y sus decisiones están publicados en [`docs/arquitectura/modelo-identidad.md`](docs/arquitectura/modelo-identidad.md). El SVG se genera desde la fuente `.mmd` usando Mermaid CLI.

## Flujo de trabajo

Este repositorio sigue el flujo de ramas `feature/* → develop → qa → release/* → main` (con `hotfix/*` directo a `main`) y la convención de commits `tipo(scope): GR-000 descripcion-breve`, según el lineamiento oficial del proyecto.
