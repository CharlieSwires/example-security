# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre

# TOTP is defined in UTC epoch seconds. Pin the container and JVM timezone so
# local daylight-saving changes cannot alter any wall-clock interpretation.
ENV TZ=Etc/UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
