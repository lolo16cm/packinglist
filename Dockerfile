# Stage 1: Build the Spring Boot app
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime container
FROM eclipse-temurin:17-jre
# Alternative options if the above doesn't work:
# FROM openjdk:17-jre-slim
# FROM eclipse-temurin:17
# FROM amazoncorretto:17

RUN apt-get update && apt-get install -y \
    fontconfig \
    fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/packinglist-*.jar app.jar

RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
