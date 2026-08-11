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

# Render 대시보드에서 빠뜨려도 최소한 prod 프로필로는 뜨도록 이미지 자체에
# 기본값을 심어둔다(코드 리뷰 지적 — 이게 없으면 SPRING_PROFILES_ACTIVE 설정을
# 깜빡했을 때 application.properties가 아예 없는 이미지에서 알 수 없는
# DataSource 에러로 죽어서 원인 파악이 어려움). Render 환경변수로 얼마든지
# 덮어쓸 수 있다.
ENV SPRING_PROFILES_ACTIVE=prod

# Render가 주입하는 $PORT를 application-prod.properties의 server.port=${PORT:8080}가 읽는다
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
