FROM openjdk:17-alpine

COPY target/default+uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 8080

ENTRYPOINT exec java -server \
                     -Xmx1250m \
                     -XX:+UseStringDeduplication \
                     -jar \
                     /enion-backend/app.jar
