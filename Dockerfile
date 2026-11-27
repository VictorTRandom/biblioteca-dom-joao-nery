FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
COPY frontend/public frontend/public
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/biblioteca.jar /app/biblioteca.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/biblioteca.jar"]
