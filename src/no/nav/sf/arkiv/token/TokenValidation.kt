package no.nav.sf.henvendelse.api.proxy.token

import mu.KotlinLogging
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Request
import java.io.File
import java.net.URL

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

interface TokenValidator {
    fun firstValidToken(request: Request): JwtToken?
}

class DefaultTokenValidator : TokenValidator {
    private val azureAlias = "azure"
    private val azureUrl = System.getenv(env_AZURE_APP_WELL_KNOWN_URL)
    private val azureAudience = System.getenv(env_AZURE_APP_CLIENT_ID)?.split(',') ?: listOf()

    private val log = KotlinLogging.logger { }

    private val callerList: MutableMap<String, Int> = mutableMapOf()

    private val multiIssuerConfiguration = MultiIssuerConfiguration(
        mapOf(
            azureAlias to IssuerProperties(URL(azureUrl), azureAudience)
        )
    )

    private val jwtTokenValidationHandler = JwtTokenValidationHandler(multiIssuerConfiguration)

    fun containsValidToken(request: Request): Boolean {
        val firstValidToken = jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken
        return firstValidToken != null
    }

    var latestValidationTime = 0L

    override fun firstValidToken(request: Request): JwtToken? {
        val result: JwtToken? = jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken
        if (result == null) {
            File("/tmp/novalidtoken").writeText(request.toMessage())
        }
        return result
    }

    private fun Request.toNavRequest(): HttpRequest {
        val req = this
        return object : HttpRequest {
            override fun getHeader(headerName: String): String {
                return req.header(headerName) ?: ""
            }
        }
    }
}
