FROM node:22-bookworm-slim AS ui
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build/backend
COPY backend/pom.xml ./
COPY backend/src/ src/
COPY --from=ui /build/frontend/dist/ /build/frontend/dist/
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system acme && useradd --system --gid acme acme \
    && mkdir -p /var/data && chown acme:acme /var/data
COPY --from=build /build/backend/target/salary-manager-1.0.0.jar /app/salary-manager.jar
ENV DATABASE_URL=jdbc:h2:file:/var/data/acme
EXPOSE 8080
COPY scripts/container-entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]
