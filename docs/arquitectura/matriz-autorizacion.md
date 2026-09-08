# Matriz de autorización

Catálogo completo de los endpoints de `gr-user-microservice` y de quién puede invocarlos. Es la fuente de verdad de HU-1.7: si un endpoint no aparece aquí, no está autorizado a existir.

La restricción vive en el servidor. Ocultar un botón en la interfaz no es un control de seguridad.

## Convenciones

| Símbolo | Significado |
| --- | --- |
| ✅ | permitido |
| ❌ | rechazado con `403 Forbidden` |
| 🔗 | permitido **solo si** existe una relación entre la persona y el recurso concreto |
| 🔓 | público, no requiere sesión |

Los tres roles del sistema son `ADMINISTRACION`, `RESIDENTE` y `VIGILANTE`. El rol viaja en el claim `roles` del access token. La distinción entre propietario y arrendatario **no** es un rol: viaja en el claim `tipoResidente`, para no multiplicar roles en el token.

## Endpoints de autenticación

Son la excepción prevista por el criterio de aceptación: sin ellos nadie podría obtener una sesión.

| Método | Ruta | Acceso | Notas |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 🔓 | Responde `401` genérico ante cualquier fallo, sin distinguir si el correo existe |
| `POST` | `/api/v1/auth/refresh` | 🔓 | Requiere la cookie `refresh_token` válida y no revocada |
| `POST` | `/api/v1/auth/logout` | 🔓 | Idempotente |
| `POST` | `/api/v1/auth/password-reset` | 🔓 | Responde `202` exista o no el correo |
| `POST` | `/api/v1/auth/password-reset/confirm` | 🔓 | El token de un solo uso es la credencial |

Aunque sean públicos, todos exigen el encabezado `X-XSRF-TOKEN` por ser mutaciones.

## Apartamentos

| Método | Ruta | `ADMINISTRACION` | `RESIDENTE` | `VIGILANTE` |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/apartamentos` | ✅ | ❌ | ❌ |
| `GET` | `/api/v1/apartamentos` | ✅ | ❌ | ❌ |
| `GET` | `/api/v1/apartamentos/{id}` | ✅ | 🔗 | ❌ |
| `PUT` | `/api/v1/apartamentos/{id}` | ✅ | ❌ | ❌ |
| `DELETE` | `/api/v1/apartamentos/{id}` | ✅ | ❌ | ❌ |

**El caso 🔗 es el importante.** Un residente puede consultar el detalle de un apartamento únicamente si es su propietario o su arrendatario vigente. Cualquier otro apartamento responde `403`, aunque su rol sí habilite el endpoint. Lo resuelve `ApartmentAccessGuard.canView`, consultando `owners` y `tenants`.

El listado (`GET /api/v1/apartamentos`) queda restringido a `ADMINISTRACION` porque expone el documento, el correo y el teléfono de cada propietario. Un residente no tiene por qué acceder al directorio del conjunto.

## Arrendatarios

| Método | Ruta | `ADMINISTRACION` | `RESIDENTE` | `VIGILANTE` |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/apartamentos/{id}/arrendatarios` | ✅ | ❌ | ❌ |
| `GET` | `/api/v1/apartamentos/{id}/arrendatarios` | ✅ | ❌ | ❌ |
| `DELETE` | `/api/v1/apartamentos/{id}/arrendatarios/{arrendatarioId}` | ✅ | ❌ | ❌ |

Todos exigen `ADMINISTRACION`: la respuesta incluye documento, correo y teléfono del arrendatario.

## Personal de vigilancia

| Método | Ruta | `ADMINISTRACION` | `RESIDENTE` | `VIGILANTE` |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/vigilantes` | ✅ | ❌ | ❌ |
| `GET` | `/api/v1/vigilantes` | ✅ | ❌ | ❌ |
| `DELETE` | `/api/v1/vigilantes/{userId}` | ✅ | ❌ | ❌ |

Un vigilante **no** puede crear ni listar otras cuentas de vigilancia: administrar el personal es competencia de administración.

Estos endpoints tienen además una particularidad que ninguna otra ruta comparte: desactivar una cuenta surte efecto en la **siguiente petición** del afectado, sin esperar a que expire su access token. `JwtAuthenticationFilter` consulta el estado del usuario en cada petición autenticada, así que un vigilante desactivado recibe `401` de inmediato. El costo y la desviación frente al ADR-001 están explicados en el README.

## Respuestas ante falta de acceso

La distinción importa y se verifica en las pruebas:

| Situación | Código | Motivo |
| --- | --- | --- |
| Sin cookie de sesión, o token inválido o expirado | `401 Unauthorized` | No sabemos quién es |
| Autenticado pero el rol no habilita el endpoint | `403 Forbidden` | Sabemos quién es y no le corresponde |
| Autenticado, el rol habilita el endpoint, pero no tiene relación con el recurso | `403 Forbidden` | Autorización a nivel de instancia |
| Ruta inexistente estando autenticado | `404 Not Found` | No se filtra el mapa de rutas a anónimos |

Todas devuelven el mismo cuerpo `ApiError` con `timestamp`, `status`, `error`, `message` y `path`.

## Dónde se aplica la restricción

En **dos capas**, a propósito:

1. **Controladores**, con `@PreAuthorize`, que rechaza antes de entrar a la lógica de negocio.
2. **Servicios**, con `@PreAuthorize` sobre `ApartmentService` y `TenantService`.

La segunda capa no es redundancia decorativa. Un servicio invocado desde otro servicio, desde una tarea programada o desde un endpoint nuevo que alguien olvide anotar se saltaría por completo la primera capa. Anotar el servicio hace que la regla viaje con la operación y no con la puerta de entrada.

El `@EnableMethodSecurity` de `SecurityConfig` es lo que habilita ambas. Y `SecurityConfig` cierra con `anyRequest().authenticated()`, de modo que un endpoint nuevo sin anotación queda accesible para cualquier autenticado, no abierto al mundo: es un descuido, no una brecha, pero sigue siendo un descuido.

## Cómo se verifica

`AuthorizationMatrixIT` recorre la matriz completa contra PostgreSQL real: cada combinación de rol y endpoint, los cuatro escenarios de acceso por instancia (administración, propietario, arrendatario vigente, residente sin relación), el `401` del anónimo y la emisión de token por rol.

`ServiceLayerAuthorizationIT` verifica la segunda capa invocando los servicios **directamente**, sin pasar por HTTP, con distintos contextos de seguridad.

## Al agregar un endpoint

1. Anotarlo con `@PreAuthorize` en el controlador.
2. Anotar también el método de servicio que ejecuta la operación.
3. Agregar su fila a este documento.
4. Extender `AuthorizationMatrixIT` con la combinación de roles correspondiente.

Un endpoint sin fila en esta tabla es un endpoint sin revisar.
