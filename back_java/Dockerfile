# Multi-stage build (docs/adr/0011-docker-compose-topology): a Maven build
# stage produces the jar, then a slim JRE runtime stage runs it — the
# final image ships neither the JDK nor Maven nor build-time dependencies.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/backend-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
