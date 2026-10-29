FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

# Copy backend and frontend source directories for packaging
COPY backend/ ./backend/
COPY frontend/ ./frontend/

WORKDIR /app/backend

# Ensure wrapper script is executable and build the executable WAR
RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests

# Lightweight Java 17 runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the executable WAR from the build stage
COPY --from=build /app/backend/target/tam-solar-1.0.0.war /app/app.war

# Default fallback port for Render
ENV PORT=10000

EXPOSE 10000

# Bind to 0.0.0.0 and listen on Render's assigned $PORT
ENTRYPOINT ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.war"]
