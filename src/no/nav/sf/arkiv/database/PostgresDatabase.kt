package no.nav.sf.arkiv.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sf.arkiv.dbName
import no.nav.sf.arkiv.dbUrl
import no.nav.sf.arkiv.model.ArkivV4
import no.nav.sf.arkiv.mountPath
import no.nav.sf.arkiv.targetDbName
import no.nav.sf.arkiv.targetDbUrl
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

class PostgresDatabase(val target: Boolean = false) {

    private val log = KotlinLogging.logger { }

    private val vaultMountPath = mountPath
    private val adminUsername = "${if (target) targetDbName else dbName}-admin"
    private val username = "${if (target) targetDbName else dbName}-user"

    // Note: exposed Database connect prepares for connections but does not actually open connections
    // That is handled via transaction {} ensuring connections are opened and closed properly
    val databaseConnection = Database.connect(dataSource())

    // HikariCPVaultUtil fetches and refreshed credentials
    private fun dataSource(admin: Boolean = false): HikariDataSource =
        HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig(),
            vaultMountPath,
            if (admin) adminUsername else username
        )

    private fun hikariConfig(): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = if (target) targetDbUrl else dbUrl
            minimumIdle = 1
            maxLifetime = 26000
            maximumPoolSize = 4
            connectionTimeout = 250
            idleTimeout = 10001
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
    }

    fun create() {
        val admin = Database.connect(dataSource(admin = true))
        transaction(admin) {
            log.info { "Creating table ArkivV4" }
            SchemaUtils.create(ArkivV4)
        }
        log.info { "Create Done" }
    }

    fun grant() {
        val admin = Database.connect(dataSource(admin = true))
        transaction(admin) {
            log.info { "Granting on arkivv4" }
            exec("GRANT INSERT, SELECT ON TABLE arkivv4 TO $username")
            log.info { "Granting on arkivv4 donw" }
        }
    }

    fun reconnect() {
        log.info { "Reconnect with $username" }
        Database.connect(dataSource())
    }
}
