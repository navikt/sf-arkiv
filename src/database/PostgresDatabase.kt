package no.nav.crm.sf.arkiv.dokumentasjon.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.crm.sf.arkiv.dokumentasjon.dbName
import no.nav.crm.sf.arkiv.dokumentasjon.dbUrl
import no.nav.crm.sf.arkiv.dokumentasjon.mountPath
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

class PostgresDatabase {

    private val log = KotlinLogging.logger { }

    private val vaultMountPath = mountPath
    private val adminUsername = "$dbName-admin"
    private val username = "$dbName-user"

    val dataSource: HikariDataSource = dataSource()

    // HikariCPVaultUtil fetches and refreshed credentials
    private fun dataSource(admin: Boolean = false): HikariDataSource =
        HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig(),
            vaultMountPath,
            if (admin) adminUsername else username
        )

    private fun hikariConfig(): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = dbUrl
            minimumIdle = 1
            maxLifetime = 26000
            maximumPoolSize = 4
            connectionTimeout = 250
            idleTimeout = 10001
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
    }
}
