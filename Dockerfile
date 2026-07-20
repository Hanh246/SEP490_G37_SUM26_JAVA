# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache Maven dependencies by copying only the pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build application jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Optimize JVM configurations for Docker environments:
# - UseContainerSupport ensures JVM respects memory/CPU limits set by the orchestrator (Railway)
# - MaxRAMPercentage=75.0 leaves some memory for container OS and avoids OOM killer
# - server.port binds dynamically to the PORT env variable assigned by Railway, defaulting to 8081 locally
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dserver.port=${PORT:-8081}", \
            "-jar", \
            "app.jar"]
