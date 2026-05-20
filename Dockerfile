# ── Étape 1 : Build Maven ──────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Étape 2 : Image runtime JVM ────────────────────────────────────────────
FROM registry.access.redhat.com/ubi8/openjdk-21:1.23

ENV LANGUAGE='en_US:en'

COPY --chown=185 --from=build /app/target/quarkus-app/lib/     /deployments/lib/
COPY --chown=185 --from=build /app/target/quarkus-app/*.jar    /deployments/
COPY --chown=185 --from=build /app/target/quarkus-app/app/     /deployments/app/
COPY --chown=185 --from=build /app/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080

USER 185

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
