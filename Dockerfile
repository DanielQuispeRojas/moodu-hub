# Dockerfile para el MOODU Hub (licencias-server) en Render/Railway.
# Multi-stage: build con Maven -> corre con JRE 21.
# Asume que este Dockerfile esta en la RAIZ del repo del licencias-server.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/licencias-server-1.0.0.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
