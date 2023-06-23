package no.nav.crm.sf.arkiv.dokumentasjon.token

import io.ktor.request.ApplicationRequest
import mu.KotlinLogging
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import java.io.File
import java.net.URL

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

private val log = KotlinLogging.logger { }
const val claim_NAME = "name"

val multiIssuerConfiguration = MultiIssuerConfiguration(
    mapOf(
        "azure" to IssuerProperties(
            URL(System.getenv(env_AZURE_APP_WELL_KNOWN_URL)),
            listOf(System.getenv(env_AZURE_APP_CLIENT_ID))
        )
    )
)

private val jwtTokenValidationHandler = JwtTokenValidationHandler(multiIssuerConfiguration)

fun containsValidToken(request: ApplicationRequest): Boolean {
    val firstValidToken = jwtTokenValidationHandler.getValidatedTokens(fromApplicationRequest(request)).firstValidToken
    if (!firstValidToken.isPresent) {
        val h = request.headers.get("Authorization")
        File("/tmp/latestheaderatfailed").writeText(h ?: "null")
    }
    return firstValidToken.isPresent
}

private fun fromApplicationRequest(
    request: ApplicationRequest
): HttpRequest {
    return object : HttpRequest {
        override fun getHeader(headerName: String): String {
            return request.headers[headerName] ?: ""
        }
        override fun getCookies(): Array<HttpRequest.NameValue> {
            return arrayOf()
        }
    }
}
