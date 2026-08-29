# Multi-stage: build with the full Maven+JDK image, run on a slim JRE so the
# deployed image doesn't carry the build toolchain.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies first, so a source-only change doesn't re-download the world.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre AS run
WORKDIR /app

# Render (and most PaaS) inject PORT at runtime; application.yml already
# reads server.port from it.
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
