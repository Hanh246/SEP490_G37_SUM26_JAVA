# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build application
COPY src ./src
RUN mvn clean package -DskipTests -B


# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Give the application user ownership of the JAR
RUN chown spring:spring app.jar

USER spring:spring

EXPOSE 8081

# sh -c is required so ${PORT:-8081} is expanded
ENTRYPOINT ["sh", "-c","exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT:-8081} -jar app.jar"]