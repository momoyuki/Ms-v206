FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /opt/ms-v206
COPY --from=build /build/bin/*-jar-with-dependencies.jar ./Server.jar
COPY src/main/java/log4j.properties ./
COPY --from=build /build/src/main/java/net/swordie/ms/handlers ./src/main/java/net/swordie/ms/handlers
EXPOSE 8483 8484 8584-8593
ENTRYPOINT ["java", "--enable-preview", "-jar", "Server.jar"]
