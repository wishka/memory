# Сервер Memory. Собирается из корня репозитория (нужен Gradle wrapper):
#   docker compose -f server/docker-compose.yml up -d --build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew ./
COPY gradle ./gradle
COPY server ./server
RUN chmod +x gradlew && ./gradlew -p server installDist --no-daemon -q

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 memory && mkdir -p /data && chown memory /data
COPY --from=build /src/server/build/install/memory-server /app
USER memory
ENV PORT=8080 DATA_DIR=/data
EXPOSE 8080
ENTRYPOINT ["/app/bin/memory-server"]
