# ---- Build stage ----
FROM eclipse-temurin:23-jdk AS build
WORKDIR /app

# copy Gradle wrapper + build files first, so dependency download is cached
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true

# now copy source and build the fat jar
COPY src src
RUN ./gradlew bootJar --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:23-jre
WORKDIR /app

# copy just the built jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Cloud Run sends traffic to $PORT (default 8080); Spring must listen there
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]