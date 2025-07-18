package no.nav.sf.arkiv

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.sf.arkiv.database.DB
import no.nav.sf.arkiv.database.DB.addArchive
import no.nav.sf.arkiv.database.DB.henteArchiveV4
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.model.hasValidDokumentDato
import no.nav.sf.arkiv.model.isEmpty
import no.nav.sf.arkiv.token.DefaultTokenValidator
import no.nav.sf.arkiv.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File
import java.io.StringWriter
import java.lang.RuntimeException
import java.sql.SQLTransientConnectionException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

const val NAIS_DEFAULT_PORT = 8080

val isDev: Boolean = System.getenv("CONTEXT") == "DEV"
val mountPath = System.getenv("MOUNT_PATH")
val targetDbName = System.getenv("TARGET_DB_NAME")
val targetDbUrl = System.getenv("TARGET_DB_URL")

private val log = KotlinLogging.logger { }

class Application(
    val tokenValidator: TokenValidator = DefaultTokenValidator()
) {
    private val log = KotlinLogging.logger { }
    val gson = GsonBuilder().setPrettyPrinting().create()

    fun start() {
        log.info { "Starting ${if (isDev) "DEV" else "PROD"}" }
        apiServer(NAIS_DEFAULT_PORT).start()

        val tablesTarget = DB.listTables()

        // DB.postgresDatabase.idQuery("arkiv", tablesTarget)

        isReady = true // If made it so far connection established kubernetes can allow traffic

        // DB.postgresDatabase.reconnectWithNormalUser()
        // doAddTestData()
        doSearch()

        scheduleServerShutdown()
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    @Volatile
    var isReady: Boolean = false

    fun api(): HttpHandler = routes(
        "/static" bind static(ResourceLoader.Classpath("/static")),
        "/internal/is_alive" bind Method.GET to { Response(Status.OK) },
        "/internal/is_ready" bind Method.GET to { if (isReady) Response(Status.OK) else Response(Status.SERVICE_UNAVAILABLE) },
        "/internal/prometheus" bind Method.GET to {
            Response(Status.OK).body(
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            )
        },
        "/authping" bind Method.GET to { r ->
            Response(Status.OK).body("Auth: ${tokenValidator.firstValidToken(r) != null}")
        },
        "/arkiv" bind Method.POST to { r ->
            Metrics.requestArkiv.inc()
            try {
                val typeToken = object : TypeToken<List<ArkivModel>>() {}.type
                val arkivItems = gson.fromJson<List<ArkivModel>>(r.bodyString(), typeToken)
                val devBypass = isDev && arkivItems.first().kilde == "test"
                if (devBypass || tokenValidator.firstValidToken(r) != null) {
                    log.info { "Authorized call to Arkiv" }
                    if (arkivItems.any { !it.hasValidDokumentDato() }) {
                        Response(
                            Status.BAD_REQUEST
                        )
                            .body("One or more payload contain invalid dokumentdato (correct format is yyyy-MM-dd)")
                    } else {
                        val result = addArchive(arkivItems)
                        result.firstOrNull()?.let {
                            File("/tmp/exampleResponseEntity").writeText("First of ${result.size}" + it.toString())
                        }
                        Metrics.insertedEntries.inc(result.size.toDouble())
                        Response(Status.CREATED).body(gson.toJson(result))
                    }
                } else {
                    log.info { "Arkiv call denied - missing valid token" }
                    Response(Status.UNAUTHORIZED)
                }
            } catch (e: Exception) {
                Metrics.issues.inc()
                if (e is SQLTransientConnectionException) {
                    Response(Status.SERVICE_UNAVAILABLE).body("Caught transient connection exception, message: ${e.message}")
                } else {
                    throw e
                }
            }
        },
        "/hente" bind Method.POST to { r ->
            Metrics.requestHente.inc()
            val henteModel = gson.fromJson<HenteModel>(r.bodyString(), HenteModel::class.java)
            val devBypass = isDev && henteModel.kilde == "test"
            if (devBypass || tokenValidator.firstValidToken(r) != null) {
                log.info { "Authorized call to Hente" }
                if (henteModel.isEmpty()) {
                    Response(Status.BAD_REQUEST).body("Request contains no search parameters, that is not allowed")
                } else if (!henteModel.hasValidDokumentDato()) {
                    Response(Status.BAD_REQUEST).body("Request contains invalid dokumentdato (correct format is empty or yyyy-MM-dd)")
                } else {
                    val responses = henteArchiveV4(henteModel)
                    log.info { "Hente successful response with ${responses.size} entries" }
                    val asJson = gson.toJson(responses)
                    File("/tmp/henteresult").writeText(asJson)
                    Response(Status.OK).body(asJson)
                }
            } else {
                Response(Status.UNAUTHORIZED).body("Hente call denied - missing valid token")
            }
        }
    )
}

fun doAddTestData() {
    val archiveModel = ArkivModel(fnr = "11111", aktoerid = "11111", tema = "itest", dokumentasjon = UUID.randomUUID().toString(), dokumentdato = "2044-01-01")
    val archiveModel2 = ArkivModel(fnr = "22222", aktoerid = "22222", tema = "itest", dokumentasjon = UUID.randomUUID().toString(), dokumentdato = "2044-01-01")

    addArchive(listOf(archiveModel, archiveModel2))
}
fun doSearch() {
    val henteModel = HenteModel(aktoerid = "22222")
    try {
        File("/tmp/searchresult").writeText(henteArchiveV4(henteModel).joinToString("\n"))
    } catch (e: Exception) {
        log.error { "Exception at henteArchive test call at application boot " + e.message }
        throw RuntimeException("Exception at henteArchive test call at application boot " + e.message)
    }
}

/**
 * kill early in the morning
 */
fun scheduleServerShutdown() {
    log.info { "Will schedule shutdown..." }
    val currentDateTime = LocalDateTime.now()

    val zone = ZoneId.systemDefault()

    val nextShutdownTime = currentDateTime.with(LocalTime.of(2, 0)).atZone(zone)

    val currentTimeMillis = System.currentTimeMillis()
    val nextShutdownTimeMillis = nextShutdownTime.toInstant().toEpochMilli()

    val delayMillis = if (currentTimeMillis < nextShutdownTimeMillis) {
        nextShutdownTimeMillis - currentTimeMillis
    } else {
        println("Shutdown for next day")
        (nextShutdownTimeMillis + TimeUnit.DAYS.toMillis(3)) - currentTimeMillis
    }
    log.info { "Scheduled shutdown - time to in millis $delayMillis" }

    GlobalScope.launch {
        delay(delayMillis)
        log.info("Trigger shutdown")
        delay(3000)
        System.exit(0)
    }
}
