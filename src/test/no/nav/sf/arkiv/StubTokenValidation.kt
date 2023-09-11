package no.nav.sf.arkiv

import io.ktor.request.ApplicationRequest
import no.nav.sf.arkiv.token.Validation

class StubTokenValidation(private val isValid: Boolean) : Validation {
    override fun containsValidToken(request: ApplicationRequest) = isValid
}
