FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy backend and frontend source files
COPY backend/ ./backend/
COPY frontend/ ./frontend/

# Build the Spring Boot application
RUN chmod +x ./backend/mvnw \
    && cd ./backend \
    && ./mvnw clean package -DskipTests

# Render web service port
EXPOSE 10000

# Start Spring Boot
CMD ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/backend/target/tam-solar-1.0.0.jar"]