FROM ghcr.io/navikt/baseimages/temurin:17
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml -Xms256M -Xmx2G"
COPY build/libs/app*.jar app.jar
COPY pre-stop.sh /