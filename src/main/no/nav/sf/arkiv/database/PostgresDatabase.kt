package no.nav.sf.arkiv.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.sf.arkiv.Environment
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil

class PostgresDatabase(env: Environment) {

    private val vaultMountPath = env.mountPath
    private val adminUsername = "${env.dbName}-admin"
    private val username = "${env.dbName}-user"
    private val dbUrl = env.dbUrl

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
