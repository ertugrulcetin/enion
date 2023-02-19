FROM clojure:openjdk-14-lein-2.9.1 AS lein

COPY target/default+uberjar/enion-backend.jar /enion-backend/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/enion-backend/app.jar"]
ENTRYPOINT exec java -server \
                     -Xms2g -Xmx2g \
                     -XX:+UseG1GC \
                     -XX:+UseStringDeduplication \
                     -XX:MaxGCPauseMillis=50 \
                     -XX:+HeapDumpOnOutOfMemoryError \
                     -XX:HeapDumpPath=/crashes/my-heap-dump.hprof \
                     -jar \
                     /enion-backend/app.jar
