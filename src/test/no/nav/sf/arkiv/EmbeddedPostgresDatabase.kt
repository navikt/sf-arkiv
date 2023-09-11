package no.nav.sf.arkiv

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

class EmbeddedPostgresDatabase {
    private val embeddedPostgres = EmbeddedPostgres.builder().start()

    fun datasource(): HikariDataSource =
        HikariConfig().run {
            jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres")
            driverClassName = "org.postgresql.Driver"
            HikariDataSource(this)
        }

    fun stop() = embeddedPostgres.close()
}
