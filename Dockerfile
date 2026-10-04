# syntax=docker/dockerfile:1

# ---------- Build ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Gradle 관련 파일을 먼저 복사하여 의존성 레이어 캐시 활용
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# 소스 복사 후 실행 가능한 JAR 생성
COPY src ./src

RUN ./gradlew clean bootJar -x test --no-daemon


# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Health Check용 curl 설치 및 non-root 사용자 생성
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system appgroup \
    && useradd --system --uid 10001 --gid appgroup appuser

COPY --from=build --chown=appuser:appgroup /workspace/build/libs/app.jar ./app.jar

USER appuser

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]