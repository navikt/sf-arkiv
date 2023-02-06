package no.nav.nks.sf.arkiv.dokumentasjon.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import mu.KotlinLogging
import no.nav.nks.sf.arkiv.dokumentasjon.dbName
import no.nav.nks.sf.arkiv.dokumentasjon.dbUrl
import no.nav.nks.sf.arkiv.dokumentasjon.mountPath
import no.nav.nks.sf.arkiv.dokumentasjon.objects.ArkivV3
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class PostgresDatabase : Database {

    private val log = KotlinLogging.logger { }

    private val vaultMountPath = mountPath
    private val adminUsername = "$dbName-admin"
    private val username = "$dbName-user"

    val dataSource: HikariDataSource = dataSource()
    override val connection: Connection get() = dataSource.connection.apply { autoCommit = false }

    init {
        flywayMigrations(dataSource(admin = true))
        // dropAndCreate()
    }

    private fun dropAndCreate() {
        org.jetbrains.exposed.sql.Database.connect(dataSource(admin = true))
        transaction {
            val statement = TransactionManager.current().connection.createStatement()
            statement.execute(initSql(adminUsername))

            log.info { "Dropping table Arkiv" }
            val dropFirst = "DROP TABLE arkivv3"
            statement.execute(dropFirst)

            log.info { "Creating table Arkiv" }
            SchemaUtils.create(ArkivV3)

            // log.info { "Creating table OffsetStorage" }
            // SchemaUtils.create(OffsetStorage)
        }
        log.info { "drop and create done" }
    }

    private fun dataSource(admin: Boolean = false): HikariDataSource =
        HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig(),
            vaultMountPath,
            if (admin) adminUsername else username
        )

    private fun flywayMigrations(dataSource: HikariDataSource): Int {
        return Flyway.configure()
            .dataSource(dataSource)
            .initSql(initSql(adminUsername))
            .load()
            .migrate()
    }

    private fun initSql(role: String) = """SET ROLE "$role""""

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
