# =============================================================================
# Dockerfile — Java application image for the Dashboard API.
#
# Uses a multi-stage build:
#   Stage 1 (builder): Compiles the Spring Boot application using Gradle.
#   Stage 2 (runtime): Runs the compiled JAR on a minimal JRE image.
#
# Multi-stage keeps the final image small (~200 MB) by excluding the
# full JDK, Gradle, and all build-time dependencies from the runtime.
# =============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copy Gradle wrapper and config first (Docker layer caching —
# this layer only rebuilds when build.gradle or wrapper changes)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Download dependencies (cached separately from code changes)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy application source code
COPY src/ src/

# Build the fat JAR (skip tests — they run in CI, not in image builds)
RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy only the compiled JAR from the builder stage
COPY --from=builder /build/build/libs/*.jar app.jar

# Expose the API port (matches server.port in application.yml)
EXPOSE 8085

# Run the Spring Boot application
# Using exec form so the JVM receives SIGTERM signals for graceful shutdown
ENTRYPOINT ["java", "-jar", "app.jar"]
