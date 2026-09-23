# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package -DskipTests \
    && mv target/*.jar target/app.jar

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=25s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseSerialGC", "-jar", "app.jar"]
