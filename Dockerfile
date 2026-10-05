# syntax=docker/dockerfile:1

# ---------- Build stage: compile and package the Spring Boot jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and pom first so dependencies are cached
# until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package \
    && cp target/*.jar app.jar

# ---------- Runtime stage: JRE only, non-root user ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system courtly && useradd --system --gid courtly --no-create-home courtly

COPY --from=build --chown=courtly:courtly /workspace/app.jar app.jar

USER courtly
EXPOSE 8080

# Size the heap from the container memory limit instead of host memory
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
