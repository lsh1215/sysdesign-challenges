# Build context: web-crawler/source (project root)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY common ./common
COPY crawler-shared-infra ./crawler-shared-infra
COPY crawler-worker ./crawler-worker
COPY frontier-service ./frontier-service
COPY freshness-scheduler ./freshness-scheduler
RUN chmod +x gradlew && ./gradlew --no-daemon :freshness-scheduler:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/freshness-scheduler/build/libs/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
