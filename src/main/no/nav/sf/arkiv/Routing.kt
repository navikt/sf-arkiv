package no.nav.sf.arkiv

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.prometheus.client.exporter.common.TextFormat
import no.nav.sf.arkiv.database.DB
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.model.hasValidDokumentDato
import no.nav.sf.arkiv.model.isEmpty
import no.nav.sf.arkiv.token.containsValidToken
import no.nav.sf.arkiv.token.TokenValidation
import no.nav.sf.arkiv.token.Validation
import java.io.File
import java.io.StringWriter
import java.sql.SQLTransientConnectionException

fun Routing.podAPI(appState: ApplicationState) {
    get("/internal/is_alive") {
        if (appState.alive) {
            call.respond(OK)
        } else {
            call.respond(InternalServerError)
        }
    }
    get("/internal/is_ready") {
        if (appState.ready) {
            call.respond(OK)
        } else {
            call.respond(InternalServerError)
        }
    }
}

fun Routing.prometheusAPI() {
    get("/internal/prometheus") {
        call.respond(
            OK,
            StringWriter().let { str ->
                TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                str
            }.toString()
        )
    }
}

fun Routing.henteAPI(database: DB = DB, env: Environment = Environment(), validator: Validation = TokenValidation()) {
    post("/hente") {
        Metrics.requestHente.inc()
        val requestBody = call.receive<HenteModel>()
        val devBypass = env.isDev && requestBody.kilde == "test"
        if (devBypass || validator.containsValidToken(call.request)) {
            log.info { "Authorized call to Hente" }
            if (requestBody.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Request contains no search parameters, that is not allowed")
            }
            if (!requestBody.hasValidDokumentDato()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Request contains invalid dokumentdato (correct format is empty or yyyy-MM-dd)"
                )
            }
            call.respond(OK, database.henteArchive(requestBody) + database.henteArchiveV4(requestBody))
        } else {
            log.info { "Hente call denied - missing valid token" }
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}

fun Routing.arkivAPI(database: DB = DB, env: Environment = Environment(), validator: Validation = TokenValidation()) {
    post("/arkiv") {
        Metrics.requestArkiv.inc()
        try {
            val requestBody = call.receive<Array<ArkivModel>>()
            val devBypass = env.isDev && requestBody.first().kilde == "test"
            if (devBypass || validator.containsValidToken(call.request)) {
                log.info { "Authorized call to Arkiv" }
                if (requestBody.any { !it.hasValidDokumentDato() }) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "One or more payload contain invalid dokumentdato (correct format is yyyy-MM-dd)"
                    )
                }
                val result = database.addArchive(requestBody)
                result.firstOrNull()?.let {
                    File("/tmp/exampleResponseEntity").writeText(it.toString())
                }
                Metrics.insertedEntries.inc(result.size.toDouble())
                call.respond(HttpStatusCode.Created, result)
            } else {
                log.info { "Arkiv call denied - missing valid token" }
                call.respond(HttpStatusCode.Unauthorized)
            }
        } catch (e: Exception) {
            Metrics.issues.inc()
            if (e is SQLTransientConnectionException) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    "Caught transient connection exception, message: ${e.message}"
                )
            } else {
                throw e
            }
        }
    }
}
