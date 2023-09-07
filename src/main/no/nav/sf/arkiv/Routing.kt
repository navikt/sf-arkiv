package no.nav.sf.arkiv

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.prometheus.client.exporter.common.TextFormat
import java.io.StringWriter

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
