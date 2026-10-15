# 화면(React)과 API(Spring Boot)를 하나의 이미지로 만든다.
# 빌드 단계는 빌드하는 컴퓨터의 CPU로 실행하고(--platform=$BUILDPLATFORM), 마지막 실행 단계만 대상 CPU(amd64/arm64)용이다.
# JAR와 화면 파일은 CPU와 무관하므로 arm64(Oracle 무료 VM)용 이미지도 느린 에뮬레이션 없이 만들어진다.

# 1) 프론트 빌드
FROM --platform=$BUILDPLATFORM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) 백엔드 빌드 (프론트 빌드 결과를 static에 넣어 JAR에 포함한다)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS api
WORKDIR /api
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle gradle
# Windows에서 체크아웃해 줄바꿈이 CRLF가 되어도 실행되게 한다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test \
    && cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /app.jar

# 3) 실행 이미지
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home atopass \
    && mkdir -p /data && chown atopass /data
WORKDIR /app
COPY --from=api /app.jar app.jar
USER atopass
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=65 -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
