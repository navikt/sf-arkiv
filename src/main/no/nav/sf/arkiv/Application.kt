package no.nav.sf.arkiv

import io.ktor.application.Application
import io.ktor.application.ApplicationStopped
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.sf.arkiv.database.DB.addArchive
import no.nav.sf.arkiv.database.DB.henteArchive
import no.nav.sf.arkiv.database.DB.henteArchiveV4
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.token.TokenValidation
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

open class Environment {
    open val isDev: Boolean = System.getenv("KTOR_ENV") == "dev"
    val mountPath = System.getenv("MOUNT_PATH")
    val dbName = System.getenv("DB_NAME")
    val dbUrl = System.getenv("DB_URL")
}
val log = KotlinLogging.logger { }

@OptIn(DelicateCoroutinesApi::class)
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val appState = monitorState(this)

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
        podAPI(appState)
        prometheusAPI()
        henteAPI()
        arkivAPI()
        get("/authping") {
            // simpler than the ones above, but do at some point factor this out similarly.
            log.info { "Incoming call authping" }
            val valid = TokenValidation().containsValidToken(call.request)
            call.respond(HttpStatusCode.OK, "Valid auth $valid")
        }
    }

    // doAddTestData()
    // health check
    doSearch()
    scheduleServerShutdown()
}

private fun monitorState(app: Application): ApplicationState =
    ApplicationState().apply {
        app.environment.monitor.subscribe(ApplicationStopped) {
            ready = false
            alive = false
        }
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
        (nextShutdownTimeMillis + TimeUnit.DAYS.toMillis(1)) - currentTimeMillis
    }
    log.info { "Scheduled shutdown - time to in millis $delayMillis" }

    GlobalScope.launch {
        delay(delayMillis)
        log.info("Trigger shutdown")
        delay(3000)
        System.exit(0)
    }
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)
