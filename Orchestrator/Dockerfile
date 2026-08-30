# -----------------------------
# Orchestrator — image multi-étapes
# Build : Gradle (via wrapper) sur JDK 17
# Runtime : JRE 17 (image légère)
# -----------------------------

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY app ./app

# Le wrapper est généré sous Windows : on corrige les fins de ligne CRLF
# pour pouvoir l'exécuter dans le conteneur Linux.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew :app:bootJar --no-daemon -x test

# -----------------------------
FROM eclipse-temurin:17-jre-jammy

# curl est utilisé par le healthcheck Docker.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/app/build/libs/*.jar app.jar

EXPOSE 9092

ENTRYPOINT ["java", "-jar", "app.jar"]
