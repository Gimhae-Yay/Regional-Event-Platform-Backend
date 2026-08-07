FROM amazoncorretto:21-al2023 AS build

WORKDIR /workspace

RUN dnf install -y findutils && dnf clean all

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .

RUN chmod +x gradlew

COPY src src

RUN ./gradlew --no-daemon bootJar

FROM amazoncorretto:21-al2023-headless

WORKDIR /app

COPY --chown=10001:10001 --from=build /workspace/build/libs/*.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
