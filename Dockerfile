# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install FFmpeg (Important: YouTube clones usually need this for video processing/thumbnails)
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# Copy the jar from build stage
COPY --from=build /app/target/*.jar video-service.jar

# Expose the port from your properties
EXPOSE 8082

# Run with optimized memory settings for large file processing
ENTRYPOINT ["java", "-Xmx1024m", "-jar", "video-service.jar"]