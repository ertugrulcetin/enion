FROM openjdk:8-alpine

COPY target/uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/enion-backend/app.jar"]
