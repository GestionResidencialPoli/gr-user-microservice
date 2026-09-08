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
- Docker Desktop o Docker Engine con Docker Compose v2+
- Maven (o usar el wrapper `./mvnw` incluido en el repo)

## Configuración

El servicio se conecta a PostgreSQL mediante variables de entorno, con valores por defecto pensados para un ambiente local. Las variables disponibles están documentadas en [`.env.example`](.env.example):

| Variable      | Descripción                       | Valor por defecto                             |
|---------------|------------------------------------|------------------------------------------------|
| `DB_URL`      | URL JDBC de conexión a PostgreSQL  | `jdbc:postgresql://localhost:5432/gr_user_db` |
| `DB_USERNAME` | Usuario de la base de datos        | `postgres`                                     |
| `DB_PASSWORD` | Contraseña de la base de datos     | `postgres`                                     |
| `JPA_SHOW_SQL` | Muestra las sentencias SQL de JPA | `false`                                        |
| `JWT_SECRET` | Clave de firma HMAC del access token (obligatoria, mínimo 32 caracteres) | *(sin valor por defecto, la app no arranca sin ella)* |
| `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` | Minutos de vigencia del access token | `15` |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | Días de vigencia del refresh token | `7` |
| `CORS_ALLOWED_ORIGINS` | Origen(es) permitidos para el frontend Next.js | `http://localhost:3000` |
| `COOKIE_SECURE` | Marca `Secure` en las cookies de sesión; `false` solo para desarrollo local sin HTTPS | `true` |

### Autenticación por cookies

La sesión se maneja con dos cookies `HttpOnly`, `Secure` y `SameSite=Strict`, nunca con encabezado `Authorization`:

- `access_token`: JWT de corta duración, enviado en cada request (`Path=/`).
- `refresh_token`: token opaco de larga duración, acotado a `Path=/api/v1/auth`, respaldado por la tabla `refresh_tokens` para poder revocarlo.

Endpoints disponibles:

- `POST /api/v1/auth/login`: valida `{ email, password }` contra la base de datos (BCrypt) y, si el usuario existe, está activo y la contraseña coincide, emite ambas cookies. Ante cualquier fallo responde 401 con un mensaje genérico que no distingue si el correo existe.
- `POST /api/v1/auth/refresh`: rota el refresh token (revoca el actual, emite uno nuevo) y renueva el access token.
- `POST /api/v1/auth/logout`: revoca el refresh token en base de datos y limpia ambas cookies.
- `POST /api/v1/auth/password-reset`: solicita el restablecimiento indicando `{ email }`. Responde `202` **siempre**, exista o no el correo.
- `POST /api/v1/auth/password-reset/confirm`: recibe `{ token, newPassword }`, actualiza la contraseña y consume el token.

Al usar cookies, las peticiones que cambian estado (`POST`, `PUT`, `PATCH`, `DELETE`) requieren protección CSRF: el servidor expone una cookie legible `XSRF-TOKEN` que el frontend debe reenviar en el encabezado `X-XSRF-TOKEN`. El frontend debe además llamar con `credentials: "include"` para que el navegador envíe las cookies en peticiones cross-origin.

### Cerrar sesión y la ventana del access token

Cerrar sesión no es solo borrar las cookies en el navegador. El frontend **debe** invocar `POST /api/v1/auth/logout`; es lo único que revoca el refresh token en el servidor. Si el cliente descarta las cookies sin llamar al endpoint, ese refresh token sigue activo hasta que expire, y cualquiera que lo tenga puede seguir renovando la sesión durante días.

La llamada requiere el encabezado `X-XSRF-TOKEN` como cualquier otra mutación. Sin él la petición recibe `403` y **la sesión no se cierra**: el refresh token queda intacto. El endpoint es idempotente y responde `204` incluso sin cookie de refresco o si se invoca dos veces, así que el frontend puede llamarlo sin comprobar antes si la sesión sigue viva. Solo revoca la sesión cuyo refresh token se envía; las demás sesiones del mismo usuario, por ejemplo en otro dispositivo, siguen activas.

#### Limitación conocida

Tras cerrar sesión, **el access token que ya se había emitido sigue siendo criptográficamente válido hasta que expire**, como máximo `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` (15 minutos por defecto). El servidor no lo invalida porque no lo puede invalidar: un JWT sin estado se valida comprobando su firma y su fecha de expiración, sin consultar la base de datos. No existe lista de revocación.

Esto significa que quien conserve una copia de ese access token (una extensión del navegador, un proxy, un registro, el historial de una herramienta de red) puede seguir invocando endpoints protegidos durante esa ventana, aunque el usuario ya haya cerrado sesión.

Es una diferencia conceptual con las sesiones en servidor, donde cerrar sesión destruye la sesión y el efecto es inmediato:

| | Sesión en servidor | JWT sin estado (este proyecto) |
| --- | --- | --- |
| Efecto del cierre de sesión | inmediato | inmediato solo para el refresh token |
| Access token tras cerrar sesión | inválido | válido hasta expirar (≤ 15 min) |
| Costo de validar | consulta de estado compartido | verificación de firma, sin E/S |
| Varias réplicas | requiere estado compartido | sin estado compartido |

La decisión fue deliberada y está documentada en [`docs/decisiones/ADR-001-estrategia-tokens.md`](docs/decisiones/ADR-001-estrategia-tokens.md), que evaluó las alternativas y aceptó esta ventana de 15 minutos para el alcance actual (Módulo 1, una sola réplica). Introducir una lista de revocación reintroduciría estado compartido entre réplicas, que es justo lo que la arquitectura sin estado busca evitar.

Consecuencias prácticas para quien construya el frontend:

- Llamar siempre a `POST /api/v1/auth/logout`, no basta con limpiar el almacenamiento local.
- Redirigir al inicio de sesión y bloquear el acceso a las pantallas protegidas por ruta, sin asumir que el token dejó de servir.
- Reducir `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` acorta la ventana a costa de más renovaciones.

El ADR-001 dejó anotado además que el criterio de aceptación de HU-1.6 debería exigir explícitamente que el cliente invoque el endpoint y que el servidor confirme `revoked_at` no nulo, no solo que el navegador borre las cookies.

### Recuperar el acceso cuando se olvida la contrasena

El flujo tiene dos pasos y no requiere sesion: ambos endpoints son publicos porque quien los usa, por definicion, no puede autenticarse.

1. `POST /api/v1/auth/password-reset` con `{ email }` genera un token de un solo uso con **30 minutos** de vigencia.
2. `POST /api/v1/auth/password-reset/confirm` con `{ token, newPassword }` actualiza la contrasena y marca el token como consumido.

Propiedades de seguridad del flujo:

- **No revela que correos existen.** La respuesta es `202` con cuerpo vacio tanto si el correo esta registrado como si no. La diferencia solo se ve en el registro del servidor, nunca en la respuesta. Por eso tampoco existe una restriccion de base de datos que pueda hacer fallar la peticion para un correo registrado y no para uno desconocido: eso reintroduciria el canal de enumeracion por la puerta de atras.
- **Se almacena el hash SHA-256 del token, nunca el valor en claro.** Se reutiliza el mismo mecanismo que la tabla `refresh_tokens`, asi que ni con acceso de lectura a la base se puede reconstruir un enlace valido.
- **Un solo uso.** Al consumirse queda `used_at` no nulo y cualquier reintento responde `400`.
- **Solicitar un enlace nuevo invalida el anterior.** Solo el ultimo enviado sirve.
- **Restablecer la contrasena revoca las sesiones activas** de ese usuario. No lo pide el criterio de aceptacion, pero una recuperacion de acceso que deja vivas las sesiones de quien tomo control de la cuenta no recupera nada.
- Las contrasenas se guardan con BCrypt y deben cumplir la politica minima: al menos 8 caracteres, una minuscula, una mayuscula y un digito. La validacion vive en la anotacion reutilizable `@PasswordPolicy`.

#### Limitacion conocida

**No hay envio de correo.** El token se escribe en el registro de la aplicacion con nivel `INFO`, incluyendo el identificador del usuario y el valor en claro:

```
Token de restablecimiento emitido para el usuario 42. Vence en 30 minutos. Token: xY9...
```

Es una limitacion aceptada del alcance de la etapa 1: el ticket excluye explicitamente el envio de correo. Tiene dos consecuencias que conviene tener presentes antes de cualquier despliegue real:

- Quien pueda leer los registros de la aplicacion puede restablecer la contrasena de cualquier usuario. En un entorno compartido o con los registros centralizados, eso equivale a acceso administrativo.
- Mientras exista esta limitacion, el servicio no deberia exponerse a usuarios reales.

Sustituir el registro por un envio de correo es el unico cambio necesario para cerrarla, y no altera el resto del flujo.

### Cuentas del personal de vigilancia

Endpoints bajo `/api/v1/vigilantes`, todos con rol `ADMINISTRACION`:

- `POST /api/v1/vigilantes`: crea la cuenta con nombre, documento, correo y contrasena inicial. La contrasena cumple la
  politica minima y se guarda con BCrypt.
- `GET /api/v1/vigilantes`: lista los vigilantes con su estado.
- `DELETE /api/v1/vigilantes/{userId}`: desactiva la cuenta (`status = INACTIVE`) y revoca sus refresh tokens.

#### El estado se valida en cada peticion

Desactivar un vigilante tiene efecto **inmediato**: su siguiente peticion a cualquier endpoint protegido responde `401`,
sin esperar a que expire el access token. `JwtAuthenticationFilter` consulta el estado del usuario en cada peticion,
usando el claim `uid` del token y una lectura de una sola columna por clave primaria.

Esto se aparta de la ventana de 15 minutos que el
[ADR-001](docs/decisiones/ADR-001-estrategia-tokens.md) acepto para el cierre de sesion, y es deliberado: el propio ADR
dice que tolerar esa ventana tras desactivar a quien opera el control de acceso fisico no es un riesgo aceptable para
este dominio.

El costo es una consulta a base de datos por peticion autenticada. Con una sola replica es despreciable, pero en un
escenario con varias replicas se convierte en carga sobre la base compartida. Si eso llega a pesar, la salida no es
quitar la verificacion sino cachearla con un tiempo de vida corto, aceptando una ventana acotada y explicita en lugar
de la de 15 minutos del token.

### Apartamentos y propietarios

Endpoints bajo `/api/v1/apartamentos` (requieren autenticación):

- `POST /api/v1/apartamentos`: registra un apartamento y su propietario principal. Exige rol `ADMINISTRACION`.
- `GET /api/v1/apartamentos`: listado paginado (`page`, `size`) y filtrable (`torre`, `numero`) de apartamentos activos. Exige rol `ADMINISTRACION`.
- `GET /api/v1/apartamentos/{id}`: detalle de un apartamento, activo o no. Accesible para `ADMINISTRACION` sobre cualquier apartamento, o para un `RESIDENTE` que sea propietario o arrendatario vigente de ese apartamento específico; cualquier otro caso (otro residente, un vigilante) responde `403`.
- `PUT /api/v1/apartamentos/{id}`: actualiza los datos del apartamento y de su propietario principal. Exige rol `ADMINISTRACION`.
- `DELETE /api/v1/apartamentos/{id}`: baja lógica (`activo = false`); el historial permanece consultable por id. Exige rol `ADMINISTRACION`.

El propietario se identifica por su número de documento: si ya existe una persona con ese documento, se reutiliza y se actualizan sus datos de contacto; si no, se crea un usuario nuevo sin credenciales de acceso.

La edición no permite cambiar de titular: si el documento enviado no es el del propietario principal actual, la respuesta es `409`. El cambio de titularidad requiere histórico de propietario, que no está implementado.

### Arrendatarios

Endpoints bajo `/api/v1/apartamentos/{id}/arrendatarios` (todos exigen rol `ADMINISTRACION`, porque exponen datos personales del arrendatario):

- `POST /api/v1/apartamentos/{id}/arrendatarios`: vincula un arrendatario al apartamento.
- `GET /api/v1/apartamentos/{id}/arrendatarios`: lista los arrendatarios vigentes del apartamento.
- `DELETE /api/v1/apartamentos/{id}/arrendatarios/{arrendatarioId}`: desvincula al arrendatario cerrando su arrendamiento con `end_date`; la fila se conserva como histórico.

Reglas aplicadas:

- Un apartamento inactivo no admite vincular arrendatarios (`409`).
- Una persona no puede tener dos arrendamientos vigentes al mismo tiempo (`409`), regla garantizada por el índice parcial `uk_tenants_active_user`.
- El arrendatario se identifica por documento igual que el propietario: se reutiliza la persona existente o se crea sin credenciales de acceso.

### Tipo de residente en el token

El access token incluye el claim `tipoResidente` con valor `PROPIETARIO` o `ARRENDATARIO`, ausente si la persona no tiene vínculo residencial. Se resuelve al emitir el token a partir de las tablas `owners` y `tenants`, y una titularidad tiene precedencia sobre un arrendamiento.

El propósito es que los módulos financiero, de comunicaciones y de reservas distingan a quien habita el inmueble de quien lo posee sin multiplicar roles en el token: el rol sigue siendo `RESIDENTE` y el matiz viaja en este claim. La restricción de acceso a la información financiera se aplica en el servicio que expone ese dato, no en este microservicio.

Como el claim se calcula al emitir el token, un cambio de vínculo residencial se refleja en la siguiente emisión. Un access token ya entregado conserva el valor anterior hasta expirar, dentro de la misma ventana descrita en `docs/decisiones/ADR-001-estrategia-tokens.md`.

### Levantar PostgreSQL con Docker Compose

1. Copia el archivo de variables:

```bash
cp .env.example .env
```

Flyway crea y evoluciona el esquema automáticamente al arrancar. Hibernate está configurado con `ddl-auto=validate`: valida las entidades, pero nunca crea ni modifica tablas.

En PowerShell:

```powershell
Copy-Item .env.example .env
```

2. Inicia PostgreSQL 16 y espera a que el healthcheck esté en estado `healthy`:

```bash
docker compose up -d
docker compose ps
```

El servicio queda disponible por defecto en `localhost:5432`, con la base `gr_user_db`. La información se conserva en el volumen nombrado `gr_user_postgres_data`.

Si el puerto 5432 ya está ocupado, cambia `POSTGRES_PORT` y actualiza también `DB_URL` en `.env` con el mismo puerto antes de iniciar Spring Boot.

Para detener el entorno sin perder datos:

```bash
docker compose down
```

Para eliminar deliberadamente los datos locales y comenzar de cero:

```bash
docker compose down -v
```

> `docker compose down -v` elimina el volumen local de PostgreSQL y no es recuperable desde Docker.

## Cómo ejecutar en local

```bash
./mvnw spring-boot:run
```

El servicio queda disponible en `http://localhost:8080`.

En el primer arranque contra este PostgreSQL vacío, Flyway creará el esquema automáticamente. No se deben ejecutar scripts SQL manuales para crear tablas.

`./mvnw spring-boot:run` activa por defecto el perfil `dev` (configurado en el `spring-boot-maven-plugin` del `pom.xml`), que además de crear el esquema carga los **datos semilla** descritos abajo. El jar empaquetado (el que corre en cualquier otro entorno, incluida producción) no activa ningún perfil por su cuenta: los datos semilla solo existen si alguien pide explícitamente el perfil `dev`.

### Datos semilla para desarrollo, pruebas y demostración

Con el perfil `dev` activo, la migración `db/seed/V900__seed_datos_desarrollo.sql` deja la base con:

- Un usuario por rol, con acceso real:

  | Rol | Correo | Contraseña |
  |-----|--------|------------|
  | RESIDENTE | `residente.demo@gestionresidencial.test` | `Semilla#2026` |
  | VIGILANTE | `vigilante.demo@gestionresidencial.test` | `Semilla#2026` |
  | ADMINISTRACION | `admin.demo@gestionresidencial.test` | `Semilla#2026` |

  El usuario `residente.demo` es además el propietario principal del apartamento Torre A - 101, para poder probar de punta a punta las funciones que dependen del claim `tipoResidente`.

- Dos torres (A y B) con diez apartamentos en total, cada uno con su propietario principal; tres de ellos tienen además un arrendatario vigente. Los usuarios de propietarios y arrendatarios de relleno no tienen credenciales de acceso (`password_hash = 'PENDING_ACTIVATION'`), igual que produce hoy el alta real de un propietario o arrendatario sin invitación.

> **Alcance no cubierto por esta semilla:** zonas comunes, reservas, cobros y pagos de administración no están sembrados porque ese modelo de datos todavía no existe en este microservicio (son el alcance de TEC-3.1 y TEC-5.1, ambos pendientes en el backlog). Cuando esos módulos se implementen, esta migración deberá ampliarse para cumplir el resto de los criterios de aceptación de GR-50 (zonas comunes, cobros/pagos y al menos un apartamento en mora).

Convención de versionado: cualquier futura migración de datos semilla bajo `db/seed/` debe numerarse desde `V900` en adelante, para no chocar con los números de versión de los cambios reales de esquema en `db/migration/`.

## Cómo ejecutar las pruebas

Las pruebas unitarias (sufijo `*Test`) no levantan contexto de Spring y corren en segundos:

```bash
./mvnw test
```

Las pruebas de integración (sufijo `*IT`) levantan PostgreSQL 16 mediante Testcontainers, por lo que requieren un motor Docker activo. Para ejecutar solo las de integración:

```bash
./mvnw verify -Dsurefire.skip=true
```

Para ejecutar toda la suite (unitarias + integración):

```bash
./mvnw verify
```

La versión de la imagen de PostgreSQL usada por Testcontainers coincide con la de `docker-compose.yml`.

## Modelo de identidad

El modelo entidad-relación y sus decisiones están publicados en [`docs/arquitectura/modelo-identidad.md`](docs/arquitectura/modelo-identidad.md). El SVG se genera desde la fuente `.mmd` usando Mermaid CLI.

## Flujo de trabajo

Este repositorio sigue el flujo de ramas `feature/* → develop → qa → release/* → main` (con `hotfix/*` directo a `main`) y la convención de commits `tipo(scope): GR-000 descripcion-breve`, según el lineamiento oficial del proyecto.
