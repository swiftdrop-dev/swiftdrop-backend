FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xms32m -Xmx256m"
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/webhook-service-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8089
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
