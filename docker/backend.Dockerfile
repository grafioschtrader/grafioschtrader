# syntax=docker/dockerfile:1
# Grafioschtrader backend image.
# Build context is the repository root:
#   docker build -f docker/backend.Dockerfile .

# The build stage always runs on the build host's architecture ($BUILDPLATFORM);
# Java bytecode is architecture-independent, so cross-building the arm64 image
# does not need an emulated Maven run.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY backend/ .
# -DskipTests, not -Dmaven.test.skip=true: grafiosch-server-base publishes its test sources as a
# test-jar and grafioschtrader-server depends on it in test scope. maven.test.skip suppresses that
# artifact while Maven still resolves the test classpath, so the reactor fails with
# "Could not find artifact grafiosch-server-base:jar:tests". skipTests compiles the tests without
# running them, which produces the test-jar and keeps the image build free of a database.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean install -DskipTests

FROM eclipse-temurin:25-jre-noble
# curl is needed for the docker-compose healthcheck (the JRE image ships neither curl nor wget)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --create-home gt
WORKDIR /app
COPY --from=build /build/grafioschtrader-server/target/grafioschtrader-server-*.jar app.jar
USER gt

# Container-friendly defaults: HTTP connector on 8080 (AJP binds 127.0.0.1 only and is
# useless across containers) and the actuator health endpoint exposed for healthchecks.
# The mail health indicator is off: SMTP is optional and an unreachable mail server
# must not mark the container unhealthy (would cause restart loops).
# All secrets (SPRING_DATASOURCE_PASSWORD, GT_JWT_SECRET, ...) come from docker-compose.
ENV JAVA_OPTS="-Xms256m -Xmx1792m" \
    GT_CONNECTOR_HTTP_ENABLED=true \
    GT_CONNECTOR_AJP_ENABLED=false \
    MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info \
    MANAGEMENT_HEALTH_MAIL_ENABLED=false

# Scheduled tasks (gt.eod.cron.* etc.) expect UTC — do not set TZ in this image.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
