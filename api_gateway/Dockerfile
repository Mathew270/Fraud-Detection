# =============================================================================
# Dockerfile — Java application image for the API Gateway.
#
# Uses a multi-stage build:
#   Stage 1 (builder): Compiles the Spring Boot application using Gradle.
#   Stage 2 (runtime): Runs the compiled JAR on a minimal JRE image.
# =============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copy Gradle wrapper and config first (Docker layer caching)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Download dependencies (cached separately from code changes)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy application source code (including protobuf files)
COPY src/ src/

# Build the fat JAR (skip tests — they run in CI)
RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy only the compiled JAR from the builder stage
COPY --from=builder /build/build/libs/*.jar app.jar

# Expose the API Gateway port (matches server.port in application.yml)
EXPOSE 8090

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
