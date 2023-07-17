FROM ghcr.io/navikt/baseimages/temurin:11
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml -Xms256M -Xmx2G"
COPY build/libs/app*.jar app.jar
COPY /opt/java/openjdk/bin/jmap /opt/java/openjdk/bin/jmap
COPY pre-stop.sh /
