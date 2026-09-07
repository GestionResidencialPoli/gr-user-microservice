package com.uni.usermicroservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para pruebas de integracion. El contenedor de PostgreSQL se arranca una
 * sola vez por JVM de prueba en el inicializador estatico y nunca se detiene
 * explicitamente: Testcontainers lo limpia al final de la ejecucion mediante
 * Ryuk. Deliberadamente NO se anota con {@code @Container}/{@code @Testcontainers},
 * porque esa extension detiene el contenedor al terminar la primera clase que
 * lo usa (afterAll), lo que rompe la conexion para las siguientes clases que
 * extienden esta base. Este es el patron de "contenedor singleton" recomendado
 * por Testcontainers para compartir un contenedor entre varias clases de prueba.
 *
 * La version de la imagen debe coincidir con la de docker-compose.yml.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
