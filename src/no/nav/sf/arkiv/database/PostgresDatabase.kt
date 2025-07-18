package no.nav.sf.arkiv.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sf.arkiv.model.Arkiv
import no.nav.sf.arkiv.mountPath
import no.nav.sf.arkiv.targetDbName
import no.nav.sf.arkiv.targetDbUrl
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.SocketTimeoutException
import java.time.Instant

class PostgresDatabase() {

    private val log = KotlinLogging.logger { }

    private val vaultMountPath = mountPath
    private val adminRole = "$targetDbName-admin"
    private val userTole = "$targetDbName-user"

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
                log.info { "Attempting creatin Hikari Data Source with catch of SocketTimeout causes" }
                return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
                    hikariConfig(),
                    vaultMountPath,
                    if (admin) adminRole else userTole
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
            jdbcUrl = targetDbUrl
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

    fun grant(tableName: String) {
        val admin = Database.connect(dataSource(admin = true))
        transaction(admin) {
            val quotedRoleName = "\"$userTole\""
            exec("GRANT INSERT, SELECT ON TABLE $tableName TO $quotedRoleName")
            exec("GRANT INSERT, SELECT ON TABLE $tableName TO $quotedRoleName")
        }
    }

    fun idQuery(tableName: String, refList: List<String>) {
        if (!refList.contains(tableName)) {
            log.info { "$tableName not present in reference list of tables - skip idQuery" }
            return
        }
        log.info { "Will attempt lastId fetch for $tableName" }
        Database.connect(dataSource())
        transaction() {
            // Option 1: Get the ID of the last row
            val lastRow =
                exec("SELECT id, dato FROM $tableName ORDER BY id DESC LIMIT 1") { rs ->
                    val found = rs.next()
                    val id = if (found) rs.getLong("id") else 0
                    val dato = if (found) rs.getTimestamp("dato").toInstant() else Instant.EPOCH
                    id to dato
                }
            val firstRow =
                exec("SELECT id, dato FROM $tableName ORDER BY id ASC LIMIT 1") { rs ->
                    val found = rs.next()
                    val id = if (found) rs.getLong("id") else 0
                    val dato = if (found) rs.getTimestamp("dato").toInstant() else Instant.EPOCH
                    id to dato
                }
            log.info { "First and Last ID and dato: $firstRow - $lastRow in $tableName with $userTole" }
        }
    }

    fun reconnectWithNormalUser() {
        log.info { "Reconnect with $userTole" }
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
