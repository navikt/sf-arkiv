package no.nav.sf.arkiv

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.prometheus.client.exporter.common.TextFormat
import java.io.StringWriter

fun Routing.podAPI() {

    get("/internal/is_alive") {
        call.respond(HttpStatusCode.OK)
    }

    get("/internal/is_ready") {
        call.respond(HttpStatusCode.OK)
    }
}

fun Routing.prometheusAPI() {
    get("/internal/prometheus") {
        call.respond(
            HttpStatusCode.OK,
            StringWriter().let { str ->
                TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                str
            }.toString()
        )
    }
}



