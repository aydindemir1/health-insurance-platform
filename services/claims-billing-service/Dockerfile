FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=docker.elastic.co/observability/apm-agent-java:1.56.0 /usr/agent/elastic-apm-agent.jar /app/elastic-apm-agent.jar
COPY --from=build /workspace/target/claims-billing-service-*.jar app.jar
USER spring:spring
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
