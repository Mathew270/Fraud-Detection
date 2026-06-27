# =============================================================================
# Dockerfile — Java application image for the Cluster Controller.
#
# Uses a multi-stage build:
#   Stage 1 (builder): Compiles the Spring Boot application using Gradle.
#   Stage 2 (runtime): Runs the compiled JAR on a minimal alpine JRE image
#                      with Docker CLI installed to allow docker-compose control.
# =============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/

RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine

# Install Docker CLI and Docker Compose CLI plugin for Docker-out-of-Docker (DooD) pattern
RUN apk add --no-cache docker-cli docker-cli-compose bash

WORKDIR /app

# Copy the compiled JAR
COPY --from=builder /build/build/libs/*.jar app.jar

# Expose the gRPC port
EXPOSE 9095

# Command to execute the microservice
ENTRYPOINT ["java", "-jar", "app.jar"]
