# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# gradlew/wrapper와 빌드 스크립트만 먼저 복사해서 의존성 레이어를 캐싱한다
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd --system --create-home appuser
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

# Render가 주입하는 $PORT를 application-prod.properties의 server.port=${PORT:8080}가 읽는다
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
