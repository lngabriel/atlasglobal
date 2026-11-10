FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B verify

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S atlas && adduser -S atlas -G atlas
COPY --from=build /workspace/target/atlas-global-0.1.0-SNAPSHOT.jar app.jar
USER atlas
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
