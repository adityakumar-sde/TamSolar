```powershell
@'
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app

# Copy backend and frontend folders
COPY backend/ ./backend/
COPY frontend/ ./frontend/

# Build the Spring Boot executable WAR
WORKDIR /app/backend

RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the executable WAR
COPY --from=build /app/backend/target/tam-solar-1.0.0.war /app/app.war

EXPOSE 10000

# Start the application using Render's assigned port
ENTRYPOINT ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.war"]
'@ | Set-Content -Path "D:\Tam Solar\Dockerfile" -Encoding ascii
```