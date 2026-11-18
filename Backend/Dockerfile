FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY src/main ./src/main
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/backend-*.jar ./app.jar

ENV PORT=10000
EXPOSE 10000
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
