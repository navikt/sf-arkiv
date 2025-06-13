FROM gcr.io/distroless/java21
COPY build/libs/app*.jar app.jar
COPY pre-stop.sh /
ENTRYPOINT ["java", "-Dlogback.configurationFile=logback-remote.xml", "-Djdk.http.proxyHost=webproxy.nais", "-Djdk.http.proxyPort=8088", "-Djdk.https.proxyHost=webproxy.nais", "-Djdk.https.proxyPort=8088", "-jar", "/app.jar"]
