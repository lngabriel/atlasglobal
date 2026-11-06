FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S atlas && adduser -S atlas -G atlas
COPY --from=build /workspace/target/atlas-global-0.1.0-SNAPSHOT.jar app.jar
USER atlas
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
