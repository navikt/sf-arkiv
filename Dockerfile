FROM navikt/java:11
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml -Xms1g -Xmx2g"
COPY build/libs/app*.jar app.jar