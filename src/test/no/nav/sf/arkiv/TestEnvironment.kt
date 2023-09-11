package no.nav.sf.arkiv

class TestEnvironment(private val isDevelop: Boolean = true) : Environment() {
    override val isDev: Boolean
        get() = isDevelop
}
