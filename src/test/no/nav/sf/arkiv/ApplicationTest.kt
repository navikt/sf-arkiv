package no.nav.sf.arkiv

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationTest {

    @Test
    fun `we need to do some refactor before we can use the routing defined in the Application class`() {
        with(testApplicationEngine()) {
            with(handleRequest(Get, "/internal/is_alive")) {
                assertEquals(OK, response.status())
            }
        }
    }

    private fun testApplicationEngine() =
        TestApplicationEngine().apply {
            start()
            setupJsonParsing()

            application.routing {
                get("/internal/is_alive") {
                    call.respond(OK)
                }
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
