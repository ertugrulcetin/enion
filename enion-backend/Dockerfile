FROM clojure:openjdk-13-lein-2.9.1 AS lein

COPY /home/runner/work/enion/enion/enion-backend/target/uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/enion-backend/app.jar"]
