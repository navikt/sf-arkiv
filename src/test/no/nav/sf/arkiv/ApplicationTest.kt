package no.nav.sf.arkiv

import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApplicationTest {

    private lateinit var appState: ApplicationState

    @BeforeEach
    fun setUp() {
        appState = ApplicationState()
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

    private fun testApplicationEngine() =
        TestApplicationEngine().apply {
            start()
            setupJsonParsing()
            application.routing {
                podAPI(appState)
                prometheusAPI()
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
}
