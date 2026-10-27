FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy backend and frontend source files
COPY backend/ ./backend/
COPY frontend/ ./frontend/

# Build the executable Spring Boot WAR
RUN chmod +x ./backend/mvnw \
    && cd ./backend \
    && ./mvnw clean package -DskipTests

# Render supplies PORT at runtime
EXPOSE 10000

# Start the Spring Boot application
CMD ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/backend/target/tam-solar-1.0.0.war"]
