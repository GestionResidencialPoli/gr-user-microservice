# ADR-001: Estrategia de tokens de sesión

- **Estado**: propuesta (implementada en GR-47, pendiente de aprobación del equipo)
- **Spike**: GR-51
- **Relacionado**: GR-47 (implementación), HU-1.3 (GR-40), HU-1.5 (GR-42), HU-1.6 (GR-43)

## Contexto

HU-1.5 y HU-1.6 exigen poder invalidar el acceso de un usuario de inmediato: cerrar sesión de forma segura y desactivar la cuenta de un vigilante. Un JWT sin estado es válido hasta que expira, sin importar lo que pase con la cuenta después de emitirlo. Resolver esto con una lista de revocación reintroduce estado compartido entre réplicas, que es justamente lo que una arquitectura sin estado busca evitar. La decisión debe sostenerse cuando el backend pase a correr con varias réplicas y, más adelante, se divida en microservicios.

## Alternativas evaluadas

| # | Alternativa | Latencia añadida | Estado compartido | Complejidad | Ventana de exposición tras revocar | Compatible con microservicios (etapa 2) |
|---|---|---|---|---|---|---|
| 1 | JWT único de vida corta, sin revocación | Ninguna | Ninguno | Baja | Igual al tiempo de vida del token | Sí, trivialmente |
| 2 | Access token corto + refresh token con revocación en BD | Una consulta a BD solo al refrescar, no en cada petición | Una tabla (`refresh_tokens`) | Media | Acotada al tiempo de vida del access token | Sí, la tabla se puede mover a un servicio de identidad propio |
| 3 | JWT + consulta del estado del usuario en cada petición | Una consulta a BD en **cada** petición autenticada | Alto (estado consultado constantemente) | Media | Prácticamente nula | Introduce acoplamiento fuerte entre cada servicio y la BD de identidad |
| 4 | JWT + lista de revocación en Redis | Una consulta a Redis en cada petición | Alto (Redis compartido entre réplicas) | Alta (nueva pieza de infraestructura) | Prácticamente nula | Sí, pero exige operar Redis desde ya |

## Decisión

Se adopta la **alternativa 2**: access token JWT de vida corta (15 minutos por defecto) más un refresh token opaco de vida larga (7 días), revocable en base de datos, con rotación en cada uso.

Ambos tokens se transportan en cookies `HttpOnly`, `Secure` y `SameSite=Strict` — nunca en el encabezado `Authorization` ni en `localStorage` — para que un XSS no pueda robarlos ni leerlos desde JavaScript. Al depender de cookies, se habilita protección CSRF (`CookieCsrfTokenRepository` + encabezado `X-XSRF-TOKEN`), ya que un atacante podría de otro modo aprovechar el envío automático de cookies del navegador.

El refresh token nunca se guarda en texto plano: se persiste su hash SHA-256 en la tabla `refresh_tokens` (`user_id`, `token_hash`, `expires_at`, `revoked_at`). Revocar una sesión es una escritura (`revoked_at = now()`), no una eliminación, para conservar trazabilidad. Cada `POST /api/v1/auth/refresh` revoca el refresh token usado y emite uno nuevo (rotación), de forma que reusar un refresh token robado después de su rotación queda detectado (el hash ya no coincide con ninguna fila activa).

Esto ya está implementado en GR-47: `AuthTokenService`, migración `V2__create_refresh_tokens.sql`, `POST /api/v1/auth/refresh` y `POST /api/v1/auth/logout`.

## Por qué no las otras alternativas

- **Alternativa 1** se descarta porque no resuelve la exigencia real de HU-1.5/1.6: aceptar hasta 15 minutos de ventana de exposición tras desactivar a un vigilante que hoy opera el control de acceso físico no es un riesgo aceptable para este dominio.
- **Alternativa 3** se descarta porque vuelve a poner una consulta de base de datos en el camino crítico de **cada** petición autenticada, eliminando la principal ventaja de usar JWT sin estado, y acopla cualquier futuro microservicio directamente a la base de datos de identidad en lugar de a un contrato de token.
- **Alternativa 4** se descarta por ahora por relación costo/beneficio: introduce una pieza de infraestructura nueva (Redis) y operación adicional que el equipo no necesita mientras exista una sola réplica del backend. Queda como camino natural de evolución (ver Consecuencias).

## Consecuencias

- El access token expirado dentro de una ventana de 15 minutos es la única exposición residual tras revocar una sesión; se considera aceptable para el alcance actual (Módulo 1, una sola réplica).
- "Cerrar sesión" en el cliente ya no es solo "borrar el token": `POST /api/v1/auth/logout` debe llamarse siempre para revocar el refresh token en servidor; si el cliente solo descarta las cookies sin llamar al endpoint, el refresh token sigue activo hasta expirar.
- GR-40 (login) debe emitir los tokens llamando a `AuthTokenService.issueTokens(...)` después de validar credenciales, no debe crear un mecanismo de emisión paralelo.
- GR-48 (BCrypt) es independiente de esta decisión: BCrypt protege `password_hash`, esta decisión protege la sesión posterior al login.
- Cuando el backend pase a correr con varias réplicas, la tabla `refresh_tokens` ya funciona sin cambios (es la fuente de verdad compartida vía PostgreSQL). Si la latencia de esa consulta en `/refresh` se vuelve un problema, la evolución natural es cachear el estado "revocado" en Redis (alternativa 4) solo para ese path, sin tocar el contrato de cookies ni el resto del backend.
- Al dividir el sistema en microservicios, `refresh_tokens` y `AuthTokenService` se mudan junto con el futuro servicio de identidad; los demás servicios solo necesitan poder validar la firma del access token (secreto compartido o JWKS), no conocer el refresh token en absoluto.

## Impacto en criterios de aceptación existentes

- **HU-1.6 (cerrar sesión de forma segura)**: su criterio de aceptación debe exigir explícitamente que el cliente invoque `POST /api/v1/auth/logout` y que el servidor confirme `revoked_at` no nulo para ese token, no solo la eliminación de cookies en el navegador.
- **HU-1.5 (administrar cuentas del personal de vigilancia)**: al desactivar una cuenta, además de cambiar `users.status`, la acción debe revocar todos los refresh tokens activos de ese usuario para que no pueda seguir refrescando su sesión; esto requiere un método adicional (`revocar todos los tokens de un usuario`) que todavía no existe y debe agregarse cuando se implemente HU-1.5.
- **HU-1.3 (iniciar sesión y obtener un token JWT)**: no cambia su intención, pero su criterio de aceptación debe actualizarse para reflejar que el resultado del login son dos cookies (`access_token`, `refresh_token`), no un token en el cuerpo de la respuesta.

Estos tres ajustes de criterios de aceptación quedan pendientes de que el equipo apruebe este ADR, tal como exige el criterio de terminado del spike.
