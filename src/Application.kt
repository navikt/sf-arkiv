package no.nav.crm.sf.arkiv.dokumentasjon

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.prometheus.client.exporter.common.TextFormat
import java.io.File
import java.io.StringWriter
import java.sql.SQLTransientConnectionException
import java.util.UUID
import model.ArkivModel
import model.HenteModel
import model.hasValidDokumentDato
import model.isEmpty
import mu.KotlinLogging
import no.nav.crm.sf.arkiv.dokumentasjon.database.DB.addArchive
import no.nav.crm.sf.arkiv.dokumentasjon.database.DB.henteArchive
import no.nav.crm.sf.arkiv.dokumentasjon.database.DB.henteArchiveV4
import no.nav.crm.sf.arkiv.dokumentasjon.token.containsValidToken

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val isDev: Boolean = System.getenv("KTOR_ENV") == "dev"
val mountPath = System.getenv("MOUNT_PATH")
val dbName = System.getenv("DB_NAME")
val dbUrl = System.getenv("DB_URL")

private val log = KotlinLogging.logger { }

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    routing {
        static("swagger") {
            resources("static")
            defaultResource("static/index.html")
        }
        get("/internal/is_alive") {
            call.respondText("I'm alive! :)")
        }
        get("/internal/is_ready") {
            call.respondText("I'm ready! :)")
        }
        get("/internal/prometheus") {
            call.respond(HttpStatusCode.OK,
            StringWriter().let { str ->
                TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                str
            }.toString())
        }
        post("/arkiv") {
            Metrics.requestArkiv.inc()
            try {
                val requestBody = call.receive<Array<ArkivModel>>()
                val devBypass = isDev && requestBody.first().kilde == "test"
                if (devBypass || containsValidToken(call.request)) {
                    log.info { "Authorized call to Arkiv" }
                    if (requestBody.any { !it.hasValidDokumentDato() }) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "One or more payload contain invalid dokumentdato (correct format is yyyy-MM-dd)"
                        )
                    }
                    val result = addArchive(requestBody)
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
                    call.respond(HttpStatusCode.ServiceUnavailable, "Caught transient connection exception, message: ${e.message}")
                } else {
                    throw e
                }
            }
        }
        post("/hente") {
            Metrics.requestHente.inc()
            val requestBody = call.receive<HenteModel>()
            val devBypass = isDev && requestBody.kilde == "test"
            if (devBypass || containsValidToken(call.request)) {
                log.info { "Authorized call to Hente" }
                if (requestBody.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Request contains no search parameters, that is not allowed")
                }
                if (!requestBody.hasValidDokumentDato()) {
                    call.respond(HttpStatusCode.BadRequest, "Request contains invalid dokumentdato (correct format is empty or yyyy-MM-dd)")
                }
                call.respond(HttpStatusCode.OK, henteArchive(requestBody) + henteArchiveV4(requestBody))
            } else {
                log.info { "Hente call denied - missing valid token" }
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
        /*
        get("/idexists") {
            val dokumentasjonId = call.request.queryParameters.get("dokumentasjonid") ?: ""

            call.respond(HttpStatusCode.OK, "Result searching on $dokumentasjonId: ${entryIdOfDokumentasjonId(dokumentasjonId)}")
        }
         */
    }

//  doAddTestData()
//    conditionalWait(120000)
    doSearch()
}

fun doAddTestData() {
    val archiveModel = ArkivModel(fnr = "11111", aktoerid = "11111", tema = "itest", dokumentasjon = UUID.randomUUID().toString(), dokumentdato = "2044-01-01")
    val archiveModel2 = ArkivModel(fnr = "22222", aktoerid = "22222", tema = "itest", dokumentasjon = UUID.randomUUID().toString(), dokumentdato = "2044-01-01")

    addArchive(arrayOf(archiveModel, archiveModel2))
}
fun doSearch() {
    val henteModel = HenteModel(aktoerid = "22222")
    File("/tmp/searchresult").writeText((henteArchive(henteModel) + henteArchiveV4(henteModel)).joinToString("\n"))
}
