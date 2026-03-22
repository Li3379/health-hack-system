# Multi-stage Dockerfile for HHS Backend
# Build stage: Compiles Spring Boot application with Maven
# Runtime stage: Minimal JRE image for execution

# =============================================================================
# BUILD STAGE
# =============================================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy pom.xml first for dependency caching layer
COPY pom.xml .

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package

# =============================================================================
# RUNTIME STAGE
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="HHS Team"
LABEL version="1.0.0"
LABEL description="Health Hack System Backend"

# Install wget for health checks and shadow for user management
RUN apk add --no-cache wget shadow

# Create non-root user and group for security
RUN addgroup -S hhs && \
    adduser -S hhs -G hhs -h /app -s /bin/sh

WORKDIR /app

# Create uploads directory with proper ownership
RUN mkdir -p /app/uploads && \
    chown -R hhs:hhs /app

# Copy JAR from build stage
COPY --from=build /build/target/hhs-backend-0.0.1-SNAPSHOT.jar app.jar

# Set ownership to non-root user
RUN chown hhs:hhs app.jar

# Switch to non-root user
USER hhs

# JVM optimization for containerized environments
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"

# Expose application port
EXPOSE 8082

# Health check configuration
# - interval: Time between health checks
# - timeout: Maximum time to wait for response
# - start-period: Grace period for application startup (Spring Boot needs time)
# - retries: Number of consecutive failures before unhealthy
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Entry point using shell form to expand JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]