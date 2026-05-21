# Expects a pre-built jar in backend/target/ (run `mvn package -DskipTests` first).
# Base image is glibc-based (Jammy) not Alpine: Firebase Admin SDK's native TLS
# libs are linked against glibc and SIGSEGV on musl.

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r letters && useradd -r -g letters letters
USER letters

ARG JAR_FILE=target/letters-*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
