# Multi-stage Dockerfile for Tally Backend
#
# Stage 1 (builder): Full JDK to compile and package the app
# Stage 2 (runtime): Minimal JRE to run the packaged JAR
#
# Why multi-stage?
# - Builder image is large (~500MB with JDK + Maven)
# - Runtime image is small (~200MB with JRE only)
# - Smaller images = faster deploys, less attack surface, lower storage costs

# ============================================================
# Stage 1: Build
# ============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (layer caching)
# Docker only re-downloads dependencies if pom.xml changes,
# not on every source file change. This dramatically speeds up builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copy source and build (skipping tests - CI runs them separately)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user to run the application.
# Running as root inside a container is a security risk:
# if the app is compromised, the attacker has root in the container.
RUN addgroup -S tally && adduser -S tally -G tally

# Copy only the JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set ownership to the non-root user
RUN chown tally:tally app.jar
USER tally

# Expose the application port
EXPOSE 9090

# JVM flags for containerized environments:
# -XX:+UseContainerSupport   → Respect container memory limits (not host RAM)
# -XX:MaxRAMPercentage=75.0  → Use up to 75% of available container memory
# -Djava.security.egd=...    → Faster startup via non-blocking random number source
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
