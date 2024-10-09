package no.nav.sf.arkiv.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sf.arkiv.dbName
import no.nav.sf.arkiv.dbUrl
import no.nav.sf.arkiv.model.Arkiv
import no.nav.sf.arkiv.mountPath
import no.nav.sf.arkiv.targetDbName
import no.nav.sf.arkiv.targetDbUrl
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.SocketTimeoutException

class PostgresDatabase(val target: Boolean = false) {

    private val log = KotlinLogging.logger { }

    private val vaultMountPath = mountPath
    private val adminRole = "${if (target) targetDbName else dbName}-admin"
    private val role = "${if (target) targetDbName else dbName}-user"

    // Note: exposed Database connect prepares for connections but does not actually open connections
    // That is handled via transaction {} ensuring connections are opened and closed properly
    val databaseConnection = Database.connect(dataSource())

    // HikariCPVaultUtil fetches and refreshed credentials
    private fun dataSource(admin: Boolean = false): HikariDataSource {
        val maxRetries = 5
        var currentRetry = 0
        var delayBetweenRetries = 1000L // 1 second initial delay

        while (currentRetry < maxRetries) {
            try {
                log.info { "creating Hikari Data Source with catch" }
                // Try to create the HikariDataSource with Vault integration
                return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
                    hikariConfig(),
                    vaultMountPath,
                    if (admin) adminRole else role
                )
            } catch (e: Exception) {
                currentRetry++
                log.error { "Exception on attempt $currentRetry: ${e.message}" }

                // Check if the exception or any of its causes is a SocketTimeoutException
                if (e.hasCauseOfType(SocketTimeoutException::class.java)) {
                    log.error { "Detected SocketTimeoutException as a cause." }

                    if (currentRetry < maxRetries) {
                        log.info { "Retrying in $delayBetweenRetries ms..." }
                        Thread.sleep(delayBetweenRetries) // Block the current thread for a delay

                        // Optionally, increase the delay (exponential backoff)
                        delayBetweenRetries *= 2
                    } else {
                        log.error { "Max retries reached. Unable to create HikariDataSource." }
                        throw e // Rethrow the exception after the max number of retries
                    }
                } else {
                    log.error { "Non-SocketTimeoutException encountered: ${e.message}" }
                    throw e // Rethrow if it's not a SocketTimeoutException or its cause
                }
            }
        }

        // Fallback - should not be reachable
        throw RuntimeException("Failed to create HikariDataSource after $maxRetries attempts")
    }

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
            exec("SET ROLE \"$adminRole\"")

            log.info { "Creating table Arkiv" }
            SchemaUtils.create(Arkiv)
        }
        log.info { "Create Done" }
    }

    fun grant() {
        val admin = Database.connect(dataSource(admin = true))
        transaction(admin) {
            log.info { "Granting on arkivv4" }
            val quotedRoleName = "\"$role\""
            exec("GRANT INSERT, SELECT ON TABLE arkivv4 TO $quotedRoleName")
            log.info { "Granting on arkivv4 done" }
        }
    }

    fun reconnectWithNormalUser() {
        log.info { "Reconnect with $role" }
        Database.connect(dataSource())
    }

    fun Throwable.hasCauseOfType(causeClass: Class<out Throwable>): Boolean {
        var currentCause: Throwable? = this
        while (currentCause != null) {
            if (causeClass.isInstance(currentCause)) {
                return true
            }
            currentCause = currentCause.cause
        }
        return false
    }
}
