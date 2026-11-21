FROM eclipse-temurin:21-jdk@sha256:d904709786b1568dbdad47a0c2b864169e3b9389d946c1ba3193e70334dfdb73 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre@sha256:3bf8568ff6c403c5b18c868d133f1775e3c4fd29dc12f57ca7dffbdb6e0df9b2
WORKDIR /app
# curl é usado pelo healthcheck do serviço app no Docker Compose.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/app/build/libs/srm-credit-engine.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
