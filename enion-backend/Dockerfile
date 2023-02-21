FROM openjdk:17-alpine

RUN apk update && apk add htop
RUN apk update && apk add procps

COPY target/default+uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 8080

ENTRYPOINT exec java -server \
                     -Xmx1250m \
                     -jar \
                     /enion-backend/app.jar
