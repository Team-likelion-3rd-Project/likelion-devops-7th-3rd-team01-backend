# 빌드: docker build -t newsbrief-backend .
# 같은 이미지를 APP_ROLE로 api / worker / batch로 나눠 실행한다 (docs/env.md)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# 의존성만 먼저 받아 두면 코드만 바뀌었을 때 이 단계는 캐시를 쓴다
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies -q > /dev/null
COPY src src
# 테스트는 CI에서 따로 돌린다 (MySQL·Redis 필요)
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -q && cp build/libs/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21-jre
# CI가 --build-arg GIT_SHA=<커밋 SHA>로 넣는다 → GET /actuator/info
ARG GIT_SHA=unknown
ENV GIT_SHA=$GIT_SHA
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
# 컨테이너 메모리 제한의 75%까지 힙으로 쓴다 (기본값 25%는 너무 작다)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# exec 형식이라 java가 PID 1이 되어 종료 신호(SIGTERM)를 받고 정상 종료한다
ENTRYPOINT ["java", "-jar", "app.jar"]
