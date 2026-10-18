# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/build/libs/store-api.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE 8080
# Same image runs the API (default) or the worker (SPRING_PROFILES_ACTIVE=worker).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
