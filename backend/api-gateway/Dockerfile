# Build context: backend/
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY stock-service/pom.xml stock-service/pom.xml
COPY billing-service/pom.xml billing-service/pom.xml
COPY api-gateway/src/ api-gateway/src/

RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -pl api-gateway -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/api-gateway/target/api-gateway-*-exec.jar api-gateway.jar

USER app
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health
ENTRYPOINT ["java", "-jar", "api-gateway.jar"]
