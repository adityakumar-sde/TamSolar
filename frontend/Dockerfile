FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY . .

RUN chmod +x backend/mvnw 
    && cd backend 
    && ./mvnw clean package -DskipTests

EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar /app/backend/target/tam-solar-1.0.0.jar"]