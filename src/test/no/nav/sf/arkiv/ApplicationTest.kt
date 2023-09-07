package no.nav.sf.arkiv

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import no.nav.sf.arkiv.database.DB
import no.nav.sf.arkiv.database.DB.dataSource
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.ArkivV3
import no.nav.sf.arkiv.model.ArkivV4
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.model.HenteResponse
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension::class)
class ApplicationTest {

    private lateinit var appState: ApplicationState
    private lateinit var henteModel: HenteModel
    private lateinit var arkivModel: ArkivModel
    private lateinit var objectMapper: ObjectMapper
    private lateinit var embededDatabase: EmbeddedPostgresDatabase
    private lateinit var embededDataSource: HikariDataSource
    private lateinit var testDatabase: DB

    @SystemStub
    private lateinit var environmentVariables: EnvironmentVariables

    @BeforeAll
    fun up() {
        embededDatabase = EmbeddedPostgresDatabase()
        embededDataSource = embededDatabase.datasource()
        testDatabase = DB.also { dataSource = embededDataSource }
    }

    @BeforeEach
    fun setUp() {
        appState = ApplicationState()
        henteModel = HenteModel(kilde = "test", dokumentdato = "2020-01-01")
        arkivModel = ArkivModel(kilde = "test", dokumentdato = "2020-01-01")
        objectMapper = ObjectMapper().registerKotlinModule()
        environmentVariables.set("KTOR_ENV", "dev")
        setupDatabase()
    }

    @AfterEach
    fun cleanUp() {
        tearDownDatabase()
    }

    @AfterAll
    fun down() {
        embededDatabase.stop()
    }

    @Test
    fun `is_alive should answer OK`() {
        with(testApplicationEngine()) {
            appState.alive = true
            with(handleRequest(Get, "/internal/is_alive")) {
                assertEquals(OK, response.status())
            }
        }
    }

    @Test
    fun `is_alive should answer 500`() {
        with(testApplicationEngine()) {
            appState.alive = false
            with(handleRequest(Get, "/internal/is_alive")) {
                assertEquals(HttpStatusCode.InternalServerError, response.status())
            }
        }
    }

    @Test
    fun `is_ready should answer OK`() {
        with(testApplicationEngine()) {
            appState.ready = true
            with(handleRequest(Get, "/internal/is_ready")) {
                assertEquals(OK, response.status())
            }
        }
    }

    @Test
    fun `is_ready should answer 500`() {
        with(testApplicationEngine()) {
            appState.ready = false
            with(handleRequest(Get, "/internal/is_ready")) {
                assertEquals(HttpStatusCode.InternalServerError, response.status())
            }
        }
    }

    @Test
    fun `get prometheus should answer OK`() {
        with(testApplicationEngine()) {
            with(handleRequest(Get, "/internal/prometheus")) {
                assertEquals(OK, response.status())
            }
        }
    }

    @Test
    fun `post hente with element found in db should answer OK with found element`() {
        insertDataInDB(arkivModel)
        with(testApplicationEngine()) {
            with(
                handleRequest(HttpMethod.Post, "/hente") {
                    payload(henteModel)
                }
            ) {
                assertEquals(OK, response.status())
                assertEquals(
                    arkivModel.opprettetAv,
                    response.payload<Array<HenteResponse>>().first().opprettetAv
                )
            }
        }
    }

    @Test
    fun `post hente with element not found in db should answer OK with empty payload`() {
        with(testApplicationEngine()) {
            with(
                handleRequest(HttpMethod.Post, "/hente") {
                    payload(henteModel)
                }
            ) {
                assertEquals(OK, response.status())
                assertTrue(response.payload<Array<HenteResponse>>().isEmpty())
            }
        }
    }

    private fun testApplicationEngine() =
        TestApplicationEngine().apply {
            start()
            setupJsonParsing()
            application.routing {
                podAPI(appState)
                prometheusAPI()
                henteAPI(database = testDatabase)
            }
        }

    private fun TestApplicationEngine.setupJsonParsing() {
        application.routing {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
        }
    }

    private fun TestApplicationRequest.payload(payload: Any) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(objectMapper.writeValueAsString(payload))
    }

    private inline fun <reified T> TestApplicationResponse.payload(): T =
        objectMapper.readValue(this.byteContent, T::class.java)

    private fun setupDatabase() =
        doInDatabase { SchemaUtils.create(ArkivV3, ArkivV4) }

    private fun tearDownDatabase() =
        doInDatabase { SchemaUtils.drop(ArkivV3, ArkivV4) }

    private fun insertDataInDB(arkivModel: ArkivModel) =
        doInDatabase {
            ArkivV4.insert {
                it[dato] = DateTime.now()
                it[opprettetAv] = arkivModel.opprettetAv
                it[kilde] = arkivModel.kilde
                it[dokumentasjon] = arkivModel.dokumentasjon
                it[dokumentasjonId] = arkivModel.dokumentasjonId
                it[dokumentdato] = DateTime.parse(arkivModel.dokumentdato, DB.fmt_onlyDay)
                it[aktoerid] = arkivModel.aktoerid
                it[fnr] = arkivModel.fnr
                it[orgnr] = arkivModel.orgnr
                it[tema] = arkivModel.tema
                it[konfidentiellt] = arkivModel.konfidentiellt
            }
        }

    private fun doInDatabase(block: () -> Unit) {
        Database.connect(embededDataSource)
        transaction {
            block()
        }
    }
}
