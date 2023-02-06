package no.nav.nks.sf.arkiv.dokumentasjon

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.io.File
import java.sql.SQLTransientConnectionException
import java.util.UUID
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB.Companion.addArchive
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB.Companion.henteArchive
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB.Companion.henteId
import no.nav.nks.sf.arkiv.dokumentasjon.database.PostgresDatabase
import no.nav.nks.sf.arkiv.dokumentasjon.token.containsValidToken
import org.joda.time.DateTime
import workMetrics

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

const val KTOR_ENV = "KTOR_ENV"
const val env_MOUNT_PATH = "MOUNT_PATH"
const val env_DB_NAME = "DB_NAME"
const val env_DB_URL = "DB_URL"

data class ApplicationState(
    var running: Boolean = true,
    var initialized: Boolean = false
)

val isDev: Boolean = System.getenv(KTOR_ENV) == "dev"

val mountPath = System.getenv(env_MOUNT_PATH)
val dbName = System.getenv(env_DB_NAME)
val dbUrl = System.getenv(env_DB_URL)

val postgresDatabase = PostgresDatabase()

@Serializable
data class ArkivModel(
    var opprettetAv: String = "",
    var kilde: String = "",
    var dokumentasjonId: String = "",
    var dokumentasjon: String = "",
    var dokumentdato: String = "",
    var aktoerid: String = "",
    var fnr: String = "",
    var orgnr: String = "",
    var tema: String = "",
    var konfidentiellt: Boolean = false
)

fun ArkivModel.hasValidDokumentDato(): Boolean {
    if (this.dokumentdato.isNotEmpty()) {
        try {
            val dateParsed = DateTime.parse(this.dokumentdato, DB.fmt_onlyDay)
        } catch (e: Exception) {
            return false
        }
    }
    return true
}

/*
fun String.toHenvendelseArkivModel(): ArkivModel? {
    val arkivModel: ArkivModel = ArkivModel()
    arkivModel.kilde = "Henvendelse"
    arkivModel.opprettetAv = "NKS"
    try {
        arkivModel.dokumentasjon = this.encodeB64()
        val jsonElement = Json.parseJson(this)
        for ((key, value) in jsonElement.jsonObject) {
            when (key) {
                "opprettetDato" -> arkivModel.dokumentdato = value.content.substring(0, 10)
                "henvendelseId" -> arkivModel.dokumentasjonId = value.content
                "fnr" -> arkivModel.fnr = value.content
                "aktorId" -> arkivModel.aktoerid = value.content
                "tema" -> arkivModel.tema = value.content
            }
        }
        return arkivModel
    } catch (e: Exception) {
        log.error { "Failed to map to arkiv model" }
        return null
    }
}

 */

data class HenteModel(
    val id: String = "",
    val kilde: String = "",
    val dokumentasjonId: String = "",
    val dokumentdato: String = "",
    val aktoerid: String = "",
    val fnr: String = "",
    val orgnr: String = "",
    val tema: String = ""
)

fun HenteModel.isEmpty(): Boolean {
    return id.isEmpty() && kilde.isEmpty() && dokumentdato.isEmpty() && fnr.isEmpty() && orgnr.isEmpty() && tema.isEmpty()
}

fun HenteModel.hasValidDokumentDato(): Boolean {
    if (this.dokumentdato.isNotEmpty()) {
        try {
            val dateParsed = DateTime.parse(this.dokumentdato, DB.fmt_onlyDay)
        } catch (e: Exception) {
            return false
        }
    }
    return true
}

data class ArkivResponse(
    val id: Int,
    val dato: String,
    val opprettetAv: String,
    val kilde: String,
    val dokumentasjonId: String,
    val dokumentasjonSummarized: String,
    val dokumentdato: String,
    val aktoerid: String,
    val fnr: String,
    val orgnr: String,
    val tema: String,
    val konfidentiellt: Boolean
)

data class HenteResponse(
    val id: Int,
    val dato: String,
    val opprettetAv: String,
    val kilde: String,
    val dokumentasjonId: String,
    val dokumentasjon: String,
    val dokumentdato: String,
    val aktoerid: String,
    val fnr: String,
    val orgnr: String,
    val tema: String
)

private val log = KotlinLogging.logger { }

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    // DB.initDB()

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
        get("/is_alive") {
            call.respondText("I'm alive! :)")
        }
        get("/is_ready") {
            call.respondText("I'm ready! :)")
        }
        get("/prometheus") {
            val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
            }
        }
        get("/id") {
            if (call.request.queryParameters["id"].isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "lacking id query param")
            } else {
                val id = call.request.queryParameters["id"]!!.toInt()
                call.respond(HttpStatusCode.OK, henteId(id))
            }
        }
        post("/arkiv") {
            workMetrics.requestArkiv.inc()
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
                    workMetrics.insertedEntries.inc(result.size.toDouble())
                    call.respond(HttpStatusCode.Created, result)
                } else {
                    log.info { "Arkiv call denied - missing valid token" }
                    call.respond(HttpStatusCode.Unauthorized)
                }
            } catch (e: Exception) {
                workMetrics.issues.inc()
                if (e is SQLTransientConnectionException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "Caught transient connection exception, message: ${e.message}")
                } else {
                    throw e
                }
            }
        }
        post("/hente") {
            workMetrics.requestHente.inc()
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
                call.respond(HttpStatusCode.OK, henteArchive(requestBody))
            } else {
                log.info { "Hente call denied - missing valid token" }
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
        // TODO Turned off dev options for now:
        /*
        get("/size") {
            if (call.request.queryParameters["id"].isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "lacking id query param")
            } else {
                val id = call.request.queryParameters["id"]!!.toInt()
                log.info { "This is parsed id from request : $id" }
                val response = sizeEstimate(id)
                call.respond(HttpStatusCode.OK, "Size estimate: $response")
            }
        }
        get("/hen") {
            if (kafkaJobInProgress) {
                call.respond(HttpStatusCode.Processing, "Kafka job is already in progress")
            } else if (kafkaJobResult.isEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    Kafka.testKafkaRead()
                }
                call.respond(HttpStatusCode.OK, "You started a kafka job")
            } else {
                call.respond(HttpStatusCode.OK, "Result : $kafkaJobResult")
            }
        }
        get("/resetresult") {
            kafkaJobResult = ""
            call.respond(HttpStatusCode.OK, "Reset kafka result (prep for new run)")
        }
        get("/offsetget") {
            call.respond(HttpStatusCode.OK, "Offset found: ${henteOffset()}. KafkaJIPflag: $kafkaJobInProgress")
        }

        get("/idexists") {
            val dokumentasjonId = call.request.queryParameters.get("dokumentasjonid") ?: ""

            call.respond(HttpStatusCode.OK, "Result searching on $dokumentasjonId: ${entryIdOfDokumentasjonId(dokumentasjonId)}")
        }

        get("/offsetreset") {
            storeOffset(-1L)
            call.respond(HttpStatusCode.OK, "Reset offset")
        }

        get("/sample") {
            call.respond(HttpStatusCode.OK, Kafka.kafkaSample())
        }

         */
    }

    log.info { "After routing setup" }
//    doAddTestData()
//    conditionalWait(120000)
//    doSearch()
//    log.info { "After doSearch" }
}

fun doAddTestData() {
    val archiveModel = ArkivModel(fnr = "25839399971", aktoerid = "2146876072680", tema = "itest", dokumentasjon = UUID.randomUUID().toString(), dokumentdato = "2023-02-03")
    val archiveModel2 = ArkivModel(fnr = "25839399971", aktoerid = "2146876072680", tema = "itest", dokumentasjon = UUID.randomUUID().toString(), dokumentdato = "2023-02-03")

    addArchive(arrayOf(archiveModel, archiveModel2))
}
fun doSearch() {
    val henteModel = HenteModel(fnr = "25839399971")
    File("/tmp/searchresult").writeText(henteArchive(henteModel).joinToString("\n"))
}

var kafkaJobResult = ""
var kafkaJobInProgress = false
