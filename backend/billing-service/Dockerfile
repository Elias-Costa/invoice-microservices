# Build context: backend/
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY stock-service/pom.xml stock-service/pom.xml
COPY billing-service/pom.xml billing-service/pom.xml
COPY common/src/ common/src/
COPY billing-service/src/ billing-service/src/

RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -pl billing-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/billing-service/target/billing-service-*-exec.jar billing-service.jar

USER app
EXPOSE 8082
HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8082/actuator/health
ENTRYPOINT ["java", "-jar", "billing-service.jar"]
