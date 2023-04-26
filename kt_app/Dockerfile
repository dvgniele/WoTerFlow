FROM azul/zulu-openjdk-alpine:11-jre

WORKDIR /app

COPY out/artifacts/kt_app_jar/kt_app.jar /app/app.jar

EXPOSE 3017

CMD ["java", "-jar", "/app/app.jar"]
