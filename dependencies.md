## Dependencies

<details>
<summary><strong>`no.nav.security:token-validation-core:4.1.5`</strong></summary>

## token-validation-core

**Opprinnelse:** no.nav.security:token-validation-core  
**Formål:** Validere tilgangstokener (access tokens) for sikker API-tilgang.  
**Bruk:** Sikrer at innkommende forespørsler har gyldige tokens, spesielt for NAVs sikkerhet.  
**Motivasjon:** Beskytter API-er mot uautorisert tilgang.  
**Alternativer:** Auth0, Keycloak token validatorer  
**Hvorfor valgt:** NAVs egen sikkerhetsbibliotek tilpasset interne behov.
</details>

<details>
<summary><strong>`net.minidev:json-smart:2.5.2`</strong></summary>

## json-smart

**Opprinnelse:** net.minidev:json-smart  
**Formål:** Håndtering av JSON data, parsing og generering.  
**Bruk:** Brukt som transitive avhengighet for token-validation.  
**Motivasjon:** Lett og rask JSON-håndtering.  
**Alternativer:** Jackson, Gson  
**Hvorfor valgt:** Passer godt med NAV sine sikkerhetsbiblioteker.
</details>

<details>
<summary><strong>`com.google.code.gson:gson:2.11.0`</strong></summary>

## gson

**Opprinnelse:** com.google.code.gson:gson  
**Formål:** Serialisering og deserialisering av JSON i Java/Kotlin.  
**Bruk:** Konvertere objekter til JSON og omvendt.  
**Motivasjon:** Enkel og godt kjent JSON-bibliotek.  
**Alternativer:** Jackson, Moshi  
**Hvorfor valgt:** Lettvekts og enkelt API.
</details>

<details>
<summary><strong>`org.postgresql:postgresql:42.5.5`</strong></summary>

## postgresql

**Opprinnelse:** org.postgresql:postgresql  
**Formål:** JDBC-driver for PostgreSQL database.  
**Bruk:** Databasekommunikasjon med PostgreSQL.  
**Motivasjon:** Stabil og mye brukt driver.  
**Alternativer:** HikariCP integrert med andre drivere  
**Hvorfor valgt:** PostgreSQL er valgt database for applikasjonen.
</details>

<details>
<summary><strong>`no.nav:vault-jdbc:1.3.10`</strong></summary>

## vault-jdbc

**Opprinnelse:** no.nav:vault-jdbc  
**Formål:** JDBC driver integrert med Vault for sikker databasetilgang.  
**Bruk:** Håndtering av databasepassord via Vault.  
**Motivasjon:** Sikrer hemmelige verdier i runtime.  
**Alternativer:** Manuell passordhåndtering  
**Hvorfor valgt:** Økt sikkerhet og automatisk hemmelighetshåndtering.
</details>

<details>
<summary><strong>`com.zaxxer:HikariCP:5.0.1`</strong></summary>

## HikariCP

**Opprinnelse:** com.zaxxer:HikariCP  
**Formål:** JDBC connection pool.  
**Bruk:** Effektiv håndtering av databaseforbindelser.  
**Motivasjon:** Rask, pålitelig og lavt overhead.  
**Alternativer:** Apache DBCP, c3p0  
**Hvorfor valgt:** Best ytelse og stabilitet.
</details>

<details>
<summary><strong>`ch.qos.logback:logback-classic:1.5.18`</strong></summary>

## logback-classic

**Opprinnelse:** ch.qos.logback:logback-classic  
**Formål:** Kraftig, konfigurerbar og rask logging.  
**Bruk:** Standard logging-rammeverk i mange Kotlin/Java prosjekter.  
**Motivasjon:** Pålitelig og godt støttet.  
**Alternativer:** Log4j, java.util.logging  
**Hvorfor valgt:** Standardvalg i mange Kotlin/Java prosjekter.
</details>

<details>
<summary><strong>`net.logstash.logback:logstash-logback-encoder:8.1`</strong></summary>

## logstash-logback-encoder

**Opprinnelse:** net.logstash.logback:logstash-logback-encoder  
**Formål:** Logback-modul for JSON-formatert logging, kompatibel med Logstash.  
**Bruk:** Formaterer logger for strukturert logging og integrasjon med ELK-stack.  
**Motivasjon:** Forbedrer logging for analyse og feilsøking.  
**Alternativer:** Log4j JSON appender  
**Hvorfor valgt:** Sømløs integrasjon med Logback og ELK.
</details>

<details>
<summary><strong>`org.http4k:http4k-core:5.14.4.0`</strong></summary>

## http4k-core

**Opprinnelse:** org.http4k:http4k-core  
**Formål:** Kjernen i http4k rammeverket for funksjonell HTTP-programmering.  
**Bruk:** Grunnlag for bygging av HTTP-tjenester i Kotlin.  
**Motivasjon:** Funksjonell tilnærming, lettvekts og modulær.  
**Alternativer:** Spring WebFlux, Ktor  
**Hvorfor valgt:** Enkelhet og Kotlin-first design.
</details>

<details>
<summary><strong>`org.http4k:http4k-server-netty:5.14.4.0`</strong></summary>

## http4k-server-netty

**Opprinnelse:** org.http4k:http4k-server-netty  
**Formål:** Netty-basert HTTP-serveradapter for http4k.  
**Bruk:** Kjører http4k applikasjoner på Netty-server.  
**Motivasjon:** Rask, asynkron HTTP-server med lavt minneforbruk.  
**Alternativer:** Jetty, Undertow  
**Hvorfor valgt:** God ytelse og enkel integrasjon med http4k.
</details>

<details>
<summary><strong>`io.netty:netty-handler:4.1.118.Final`</strong></summary>

## netty-handler

**Opprinnelse:** io.netty:netty-handler  
**Formål:** Netty-modul for protokollhåndtering og pipeline.  
**Bruk:** Brukes internt av http4k og Netty-server for håndtering av nettverkspakker.  
**Motivasjon:** Modularisering og utvidbarhet i Netty.  
**Alternativer:** Ingen, spesifikk for Netty.  
**Hvorfor valgt:** Påkrevd for Netty-basert HTTP-server.
</details>

<details>
<summary><strong>`io.netty:netty-common:4.1.118.Final`</strong></summary>

## netty-common

**Opprinnelse:** io.netty:netty-common  
**Formål:** Fellesverktøy og basiskomponenter for Netty.  
**Bruk:** Delte funksjoner brukt av flere Netty-moduler.  
**Motivasjon:** Grunnleggende infrastruktur for Netty.  
**Alternativer:** Ingen  
**Hvorfor valgt:** Påkrevd avhengighet for Netty.
</details>

<details>
<summary><strong>`org.http4k:http4k-client-okhttp:5.14.4.0`</strong></summary>

## http4k-client-okhttp

**Opprinnelse:** org.http4k:http4k-client-okhttp  
**Formål:** OkHttp-klientadapter for http4k.  
**Bruk:** Gjør det mulig å sende HTTP-forespørsler via OkHttp i http4k klienter.  
**Motivasjon:** Kombinerer http4k funksjonalitet med OkHttp sine ytelsesfordeler.  
**Alternativer:** http4k-client-apache, Ktor client  
**Hvorfor valgt:** Lettvekts, godt støttet HTTP-klient.
</details>

## Plugins

<details>
<summary><strong>`org.jmailen.kotlinter:3.2.0`</strong></summary>

## kotlinter

**Opprinnelse:** Gradle plugin for ktlint  
**Formål:** Kotlin-kodelinter og formattering  
**Bruk:** Sørger for automatisk kodelinting i byggeprosessen  
**Motivasjon:** Sikre kodekvalitet og enhetlig kodeformat  
**Alternativer:** Manuell kjøring av ktlint CLI  
**Hvorfor valgt:** Enkel integrasjon med Gradle, automatisering av linting
</details>

<details>
<summary><strong>`org.jetbrains.kotlin.jvm:1.9.24`</strong></summary>

## kotlin-jvm

**Opprinnelse:** org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm  
**Formål:** Kompilere Kotlin-kode til JVM bytekode.  
**Bruk:** Legger til Kotlin/JVM støtte i Gradle-bygget.  
**Motivasjon:** Offisiell plugin, bred støtte og vedlikehold.  
**Alternativer:** Kotlin multiplatform plugin.  
**Hvorfor valgt:** Standard for Kotlin/JVM prosjekter.
</details>

<details>
<summary><strong>`com.gradleup.shadow:8.3.1`</strong></summary>

## shadow

**Opprinnelse:** com.gradleup:shadow  
**Formål:** Gradle plugin for å lage "fat" eller "uber" JAR-filer som inkluderer alle avhengigheter.  
**Bruk:** Pakker applikasjon og alle nødvendige biblioteker i én JAR for enkel distribusjon.  
**Motivasjon:** Forenkler distribusjon og kjøring av applikasjoner uten behov for å håndtere avhengigheter separat.  
**Alternativer:** Shadow plugin fra John Engelman (org.gradle.plugins.shadow), Spring Boot plugin  
**Hvorfor valgt:** Modifisert og oppdatert versjon med ekstra funksjonalitet og forbedringer i forhold til eldre shadow-plugins.
</details>

