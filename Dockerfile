FROM ghcr.io/navikt/baseimages/temurin:11
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml -Xms256M -Xmx2G"
COPY build/libs/app*.jar app.jar
run ls -l /opt/java/openjdk/bin/
COPY pre-stop.sh /
