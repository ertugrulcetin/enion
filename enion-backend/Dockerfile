FROM clojure:openjdk-14-lein-2.9.1 AS lein

COPY target/default+uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 8080

ENTRYPOINT exec java -server \
                     -Xms1750m -Xmx1750m \
                     -XX:+UseStringDeduplication \
                     -jar \
                     /enion-backend/app.jar
