package no.nav.sf.arkiv

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import no.nav.sf.arkiv.database.DB
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.ArkivV3
import no.nav.sf.arkiv.model.ArkivV4
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.model.HenteResponse
import no.nav.sf.arkiv.token.Validation
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationTest {

    private lateinit var appState: ApplicationState
    private lateinit var objectMapper: ObjectMapper
    private lateinit var henteModel: HenteModel
    private lateinit var arkivModel: ArkivModel
    private lateinit var embededDatabase: EmbeddedPostgresDatabase
    private lateinit var embededDataSource: HikariDataSource
    private lateinit var testDatabase: DB
    private lateinit var testEnvironment: Environment
    private lateinit var testValdator: Validation

    @BeforeAll
    fun up() {
        embededDatabase = EmbeddedPostgresDatabase()
        embededDataSource = embededDatabase.datasource()
        testDatabase = DB.apply { dbSouce = embededDataSource }
        setupDatabaseSchemas()
    }

    @BeforeEach
    fun setUp() {
        appState = ApplicationState()
        objectMapper = ObjectMapper().registerKotlinModule()
        henteModel = HenteModel(kilde = "test", dokumentdato = "2020-01-01")
        arkivModel = ArkivModel(kilde = "test", dokumentdato = "2020-01-01")
        testEnvironment = TestEnvironment()
        testValdator = StubTokenValidation(true)
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
    fun `post hente should answer OK with arkivModel found in db`() {
        insertDataInDB(arkivModel)
        with(testApplicationEngine()) {
            with(
                handleRequest(Post, "/hente") {
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
    fun `post hente with not valid token should answer unauthorized`() {
        henteModel = HenteModel(kilde = "prod")
        testValdator = StubTokenValidation(false)
        with(testApplicationEngine()) {
            with(
                handleRequest(Post, "/hente") {
                    payload(henteModel)
                }
            ) {
                assertEquals(Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `post hente with wrong dokumentdato should answer bad request`() {
        henteModel = HenteModel(kilde = "test", dokumentdato = "01-01-2020")
        with(testApplicationEngine()) {
            with(
                handleRequest(Post, "/hente") {
                    payload(henteModel)
                }
            ) {
                assertEquals(BadRequest, response.status())
            }
        }
    }

    @Test
    fun `post hente with empty payload should answer bad request`() {
        henteModel = HenteModel()
        testValdator = StubTokenValidation(true)
        with(testApplicationEngine()) {
            with(
                handleRequest(Post, "/hente") {
                    payload(henteModel)
                }
            ) {
                assertEquals(BadRequest, response.status())
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
                henteAPI(database = testDatabase, env = testEnvironment, validator = testValdator)
            }
        }

    private fun setupDatabaseSchemas() =
        doInTransaction { SchemaUtils.create(ArkivV3, ArkivV4) }

    private fun TestApplicationEngine.setupJsonParsing() {
        application.routing {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
        }
    }

    private fun insertDataInDB(arkivModel: ArkivModel) =
        doInTransaction {
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

    private fun TestApplicationRequest.payload(payload: Any) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(objectMapper.writeValueAsString(payload))
    }

    private inline fun <reified T> TestApplicationResponse.payload(): T =
        objectMapper.readValue(this.byteContent, T::class.java)

    private fun doInTransaction(dbStuff: () -> Unit) {
        Database.connect(embededDataSource)
        transaction {
            dbStuff()
        }
    }
}
