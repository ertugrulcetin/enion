FROM clojure:openjdk-14-lein-2.9.1 AS lein

COPY target/default+uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 8080

ENTRYPOINT exec java -server \
                     -Xms1500m -Xmx1500m \
                     -XX:+UseG1GC \
                     -XX:+UseStringDeduplication \
                     -XX:MaxGCPauseMillis=50 \
                     -XX:+HeapDumpOnOutOfMemoryError \
                     -XX:HeapDumpPath=/enion-backend/dumps \
                     -jar \
                     /enion-backend/app.jar
