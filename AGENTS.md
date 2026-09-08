# AGENTS.md

## Propósito del repositorio

`gr-user-microservice` es el microservicio de identidad y autenticación de Gestión Residencial. Administra usuarios, roles y la relación residencial de las personas con los apartamentos como propietarios o arrendatarios.

El producto completo cubre tres perfiles funcionales:

- `RESIDENTE`: consulta información de su unidad y usa servicios residenciales.
- `VIGILANTE`: opera control de acceso, visitantes y parqueaderos.
- `ADMINISTRACION`: gestiona comunicaciones, configuración y cobros.

Este repositorio solo debe asumir responsabilidades de identidad, autenticación, autorización y datos residenciales necesarios para identificar a una persona. No incorporar aquí lógica de reservas, visitantes, parqueaderos, publicaciones o cartera salvo que exista una decisión arquitectónica explícita.

## Stack y comandos

- Java 17.
- Spring Boot 4.1.x.
- Spring Data JPA / Hibernate.
- Spring Security.
- PostgreSQL 14 o superior.
- Flyway como único mecanismo de evolución del esquema.
- Maven Wrapper.

En Windows:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

En Linux o macOS:

```bash
./mvnw clean verify
./mvnw spring-boot:run
```

Las pruebas de integración usan PostgreSQL 16 mediante Testcontainers. Antes de ejecutarlas debe existir un motor Docker activo. No sustituir PostgreSQL por H2: las restricciones, tipos e índices parciales deben validarse contra el motor real.

La prueba que comprueba `ddl-auto=validate` provoca deliberadamente un error de arranque por una columna faltante. Es normal ver ese stack trace si Maven termina con `BUILD SUCCESS` y todas las pruebas quedan en verde.

Comandos separados para unitarias y de integración:

```bash
./mvnw test                          # solo unitarias (*Test), sin Docker, en segundos
./mvnw verify -Dsurefire.skip=true    # solo de integración (*IT), requiere Docker
./mvnw verify                         # ambas
```

Las clases de integración que no mutan el esquema deben extender `com.uni.usermicroservice.support.AbstractIntegrationTest`, que expone un contenedor de PostgreSQL compartido entre clases (patrón singleton, arrancado en un bloque estático, nunca detenido explícitamente). Una prueba que necesite romper o alterar el esquema deliberadamente debe declarar su propio contenedor `@Container`/`@Testcontainers` en vez de usar el compartido, para no dejar el esquema roto para las demás.

## Estructura relevante

```text
src/main/java/com/uni/usermicroservice/
  identity/domain/             Entidades y tipos del dominio de identidad
src/main/resources/
  application.properties      Configuración de datasource, Flyway y JPA
  db/migration/                Migraciones Flyway versionadas
src/test/java/                 Pruebas unitarias y de integración
docs/arquitectura/             Decisiones y diagramas de arquitectura
```

Mantener separadas las responsabilidades de API, aplicación, dominio y persistencia cuando se agreguen nuevas capacidades. Los controladores no deben contener reglas de negocio complejas.

## Modelo de identidad vigente

Las tablas actuales son:

- `users`: credenciales e identidad base.
- `roles`: catálogo de perfiles del sistema.
- `user_roles`: relación muchos a muchos entre usuarios y roles.
- `apartments`: unidades privadas identificadas por torre y número.
- `owners`: relación de titularidad entre usuarios y apartamentos.
- `tenants`: periodos de arrendamiento entre usuarios y apartamentos.

Reglas importantes protegidas por PostgreSQL:

- `(torre, numero)` es único mediante `uk_apartments_torre_numero`.
- Los correos se almacenan normalizados en minúsculas y no se repiten.
- El documento de identidad de cada persona (`users.document_number`) es obligatorio y no se repite.
- El coeficiente de copropiedad de un apartamento, si se registra, debe estar entre 0 (exclusivo) y 1; el área debe ser positiva.
- Un usuario no puede repetir una titularidad sobre el mismo apartamento.
- Solo puede existir un propietario marcado como principal por apartamento.
- Un usuario no puede tener dos arrendamientos vigentes al mismo tiempo.
- `end_date` no puede ser anterior a `start_date`.

Consultar antes de modificar el modelo:

- `docs/arquitectura/modelo-identidad.md`
- `docs/arquitectura/modelo-identidad.mmd`
- `src/main/resources/db/migration/V1__create_identity_schema.sql`

## Reglas obligatorias de base de datos

1. Flyway es el único dueño del esquema.
2. `spring.jpa.hibernate.ddl-auto` debe permanecer en `validate`.
3. No usar `create`, `create-drop`, `update`, `schema.sql` ni otro mecanismo paralelo para crear tablas.
4. Nunca editar una migración que ya haya sido compartida o aplicada. Crear una migración nueva con la siguiente versión.
5. Nombrar tablas, columnas, restricciones e índices en `snake_case`.
6. Declarar reglas de concurrencia e integridad en PostgreSQL, no solo como validaciones Java.
7. Toda clave foránea debe indicar explícitamente su política `ON DELETE` y contar con un índice útil cuando no esté cubierta por una clave o índice existente.
8. Los cambios destructivos requieren revisión adicional, estrategia de compatibilidad y plan de rollback.
9. Mantener las entidades JPA alineadas con tipos, longitudes, nulabilidad y nombres de la migración.
10. Un cambio de esquema debe incluir una prueba de integración sobre PostgreSQL.

## Entidades JPA y código Java

- Clases, interfaces y tipos: `PascalCase`.
- Métodos, atributos y variables: `camelCase`.
- Constantes: `UPPER_SNAKE_CASE`.
- Entidades y relaciones: carga `LAZY` por defecto, salvo una razón documentada.
- Evitar cascadas de eliminación que puedan borrar identidad o historial de manera accidental.
- No exponer entidades JPA directamente desde la API; utilizar DTOs de entrada y salida.
- Validar DTOs en el borde de entrada y mantener las reglas de negocio fuera de controladores.
- Hacer explícitos los límites transaccionales cuando una operación modifique varias relaciones.
- No incluir contraseñas en texto plano. `users.password_hash` solo almacena hashes producidos por un algoritmo seguro configurado por la aplicación.
- Evitar lógica duplicada y mantener clases y métodos pequeños y cohesionados.

## APIs

- Versionar endpoints bajo `/api/v1`.
- Usar recursos en plural y los verbos HTTP correctos.
- Mantener respuestas y errores consistentes.
- Considerar paginación, filtros y ordenamiento en endpoints de colección.
- Aplicar autorización por rol y por relación con el apartamento; un residente solo puede acceder a información pública o vinculada con su unidad.

## Documentación Mermaid

El diagrama de identidad se mantiene como fuente `.mmd` y como SVG renderizado. Cuando cambie el modelo, actualizar ambos en el mismo work item:

```bash
npx --yes @mermaid-js/mermaid-cli \
  -i docs/arquitectura/modelo-identidad.mmd \
  -o docs/arquitectura/modelo-identidad.svg \
  -b transparent
```

Inspeccionar visualmente el SVG después de generarlo y confirmar que las cardinalidades, claves y nombres coincidan con la migración.

## Configuración y secretos

- Documentar toda variable de entorno en `.env.example` y en el README.
- Nunca versionar secretos, tokens, credenciales reales ni datos personales.
- Variables actuales: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JPA_SHOW_SQL`, `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES`, `JWT_REFRESH_TOKEN_EXPIRATION_DAYS`, `CORS_ALLOWED_ORIGINS` y `COOKIE_SECURE`.
- `JWT_SECRET` es obligatoria y no tiene valor por defecto: la aplicación no debe arrancar si falta.

## Autenticación: cookies, no encabezado Authorization

Decisión vigente desde GR-47 (formalizada en `docs/decisiones/ADR-001-estrategia-tokens.md`): la sesión se transporta en dos cookies `HttpOnly`, `Secure` y `SameSite=Strict`, nunca en un encabezado `Authorization`.

- `access_token`: JWT corto (`jwt.access-token-expiration-minutes`), válido en todo el sitio (`Path=/`).
- `refresh_token`: token opaco de larga duración (`jwt.refresh-token-expiration-days`), acotado a `Path=/api/v1/auth` y respaldado por la tabla `refresh_tokens` (hash SHA-256, nunca el valor en claro) para permitir revocación server-side.
- `POST /api/v1/auth/refresh` rota el refresh token; `POST /api/v1/auth/logout` lo revoca y limpia ambas cookies.
- CSRF permanece habilitado (`CookieCsrfTokenRepository` + cookie legible `XSRF-TOKEN`): toda mutación debe incluir el encabezado `X-XSRF-TOKEN`.
- No introducir un mecanismo paralelo de autenticación (por ejemplo, aceptar `Authorization: Bearer`) sin actualizar el ADR primero.
- Los valores por defecto son solo para desarrollo local.

## Pruebas requeridas

Antes de entregar un cambio:

1. Ejecutar `clean verify` con Docker activo.
2. Confirmar que una base PostgreSQL vacía arranca y recibe todas las migraciones.
3. Probar las restricciones de base de datos afectadas, incluyendo escenarios concurrentes o duplicados cuando aplique.
4. Confirmar que Hibernate valida el esquema sin modificarlo.
5. Para cada bug, agregar una prueba de regresión.
6. Revisar `git diff --check` y comprobar que no se agregaron secretos ni archivos generados innecesarios.

## Gitflow y trazabilidad

Todo cambio debe corresponder a un work item de Jira que cumpla sus criterios de aceptación.

- `feature/*`, `fix/*` y `refactor/*` nacen desde `develop`.
- `hotfix/*` nace desde `main` y se usa únicamente para incidentes activos en producción.
- Formato de rama: `tipo/GR-123-descripcion-breve`.
- Flujo normal: `feature/* -> develop -> qa -> release/* -> main`.
- Flujo urgente: `hotfix/* -> main -> develop -> qa`.
- Las ramas de vida corta se integran con squash.
- Las ramas permanentes (`develop`, `qa`, `release/*`, `main`) se integran entre sí con merge commit.
- No encadenar ramas de trabajo desde otra rama aún no fusionada.
- No hacer push directo a ramas permanentes.
- Eliminar la rama de trabajo después del merge.

Formato de commit y título de Pull Request:

```text
tipo(scope): GR-123 descripcion breve
```

Tipos habituales: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci` y `build`.

## Pull Requests y Definition of Done

### Convención para crear Pull Requests

Cada PR debe representar un único work item de Jira y salir de una rama de trabajo actualizada con su rama destino. Para trabajo normal, el destino es `develop`; solo los `hotfix/*` se dirigen a `main`. Antes de abrirlo, integrar los cambios recientes de la rama destino mediante rebase o merge según el flujo acordado, resolver los conflictos y volver a ejecutar las verificaciones afectadas.

Usar un título breve, trazable y orientado al cambio:

```text
GR-123: descripción breve en español
```

El cuerpo se escribe en Markdown, no sustituye la historia de Jira y debe permitir que otra persona entienda y revise el cambio sin reconstruir su contexto. Usar esta plantilla, eliminando las secciones que realmente no apliquen:

```md
## Resumen
- Qué necesidad resuelve el PR.
- Qué cambió y cuál es su alcance funcional o técnico.

## Decisiones e impacto
- Decisiones relevantes, compatibilidad, configuración o riesgos.
- Para cambios de esquema: migración Flyway, restricciones e impacto de despliegue.

## Verificación
- `<comando ejecutado>` — resultado observado.
- Pruebas manuales, de integración o evidencia adicional si aplica.

## Fuera de alcance
- Trabajo relacionado que deliberadamente no se incluye, si existe.

## Checklist
- [ ] Criterios de aceptación de Jira cubiertos.
- [ ] Documentación y variables de entorno actualizadas cuando aplica.
- [ ] Sin secretos, datos personales ni archivos generados innecesarios.
```

La sección **Resumen** debe decir qué se hizo, no limitarse a copiar el título. **Decisiones e impacto** explica especialmente aquello que pueda afectar a otros servicios, datos existentes, despliegues o compatibilidad. **Verificación** contiene comandos reales y su resultado; no declarar una prueba como ejecutada si no se ejecutó. Referenciar el work item (`GR-123`) en el título y la rama garantiza la trazabilidad incluso si la plataforma no enlaza Jira automáticamente.

Para un cambio de Flyway, incluir el nombre de la migración, las tablas o restricciones afectadas y cómo se validó sobre PostgreSQL. Para una configuración de Docker, infraestructura o CI, indicar los servicios, puertos, volúmenes, variables y cualquier efecto persistente. Para un cambio de API, documentar endpoints, compatibilidad y ejemplos si alteran el contrato.

Un PR debe:

- Estar vinculado al work item.
- Explicar objetivo, alcance, impacto y decisiones relevantes.
- Incluir evidencia de las pruebas ejecutadas.
- Compilar y tener el pipeline en verde.
- Contar con mínimo dos aprobaciones.
- No tener conversaciones abiertas.
- No contener secretos ni datos sensibles.
- Actualizar documentación, migraciones y pruebas cuando corresponda.

QA valida conjuntos integrados de cambios; no reemplaza las pruebas locales, unitarias o de integración de cada rama. Un trabajo solo está terminado cuando cumple los criterios de aceptación, sus pruebas pasan, la documentación está actualizada y siguió el flujo de revisión y promoción correspondiente.
