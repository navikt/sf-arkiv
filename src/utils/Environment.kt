package no.nav.nks.sf.arkiv.dokumentasjon.utils

interface Environment {
    companion object {
        fun getEnvOrDefault(k: String, d: String = ""): String = runCatching { System.getenv(k) ?: d }.getOrDefault(d)
    }
}
