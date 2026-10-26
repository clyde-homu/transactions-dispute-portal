# One image for the whole product: the Quasar SPA is built first and served by the Spring Boot app.
# Tests run in CI (see .github/workflows/ci.yml), not in this build, so the image build only compiles and packages.

# ---- 1. SPA build ----
FROM node:24-alpine AS web
WORKDIR /web

# Install dependencies from the lockfile only, so this layer is reused until a dependency changes.
# Install scripts are skipped here because the Quasar one needs the sources; it is not needed for a build.
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --ignore-scripts --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# ---- 2. API build and layer extraction ----
FROM eclipse-temurin:25-jdk AS api
WORKDIR /build

# --chmod because a Windows checkout can lose the executable bit.
COPY --chmod=755 backend/mvnw ./
COPY backend/.mvn .mvn
COPY backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q dependency:go-offline

COPY backend/src src
# Spring Boot serves the SPA from classpath:static/, so it is packaged inside the jar.
COPY --from=web /web/dist/spa src/main/resources/static

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package

# Split the jar into dependency, loader and application layers so a code-only change rebuilds one small layer.
RUN java -Djarmode=tools -jar target/dispute-portal-*.jar extract --layers --launcher --destination /extracted

# ---- 3. Runtime ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Fixed numeric ids so file ownership and Kubernetes runAsUser stay predictable. No shell, no home directory.
RUN addgroup -g 10001 -S app \
    && adduser -u 10001 -S -G app -H -h /app -s /sbin/nologin app

# Least to most frequently changing, so a code change only invalidates the last COPY.
# The files stay root-owned: the application user can read and run them but not modify them.
COPY --from=api /extracted/dependencies/ ./
COPY --from=api /extracted/spring-boot-loader/ ./
COPY --from=api /extracted/snapshot-dependencies/ ./
COPY --from=api /extracted/application/ ./

USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Readiness, not liveness: the container is only healthy once the database and migrations are ready.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# Labels go last so changing a build argument does not invalidate any layer above.
ARG VERSION=0.0.1-SNAPSHOT
ARG REVISION=unknown
ARG CREATED=unknown
LABEL org.opencontainers.image.title="Dispute Portal" \
      org.opencontainers.image.description="Customers view transactions, raise disputes and follow their history." \
      org.opencontainers.image.source="https://github.com/clyde-homu/transactions-dispute-portal" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}"
