package no.nav.sf.arkiv.database

import mu.KotlinLogging
import no.nav.sf.arkiv.Metrics
import no.nav.sf.arkiv.isDev
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.ArkivResponse
import no.nav.sf.arkiv.model.ArkivV3
import no.nav.sf.arkiv.model.ArkivV4
import no.nav.sf.arkiv.model.DOKUMENTASJON_LENGTH
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.model.HenteResponse
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.io.File
import java.sql.ResultSet

private val log = KotlinLogging.logger { }

object DB {
    val postgresDatabase = PostgresDatabase()

    fun addArchive(requestBody: List<ArkivModel>): List<ArkivResponse> {
        if (isDev) {
            log.info { "Call to addArchive requestBody: ${requestBody.toList()} (Log in dev)" }
        } else {
            log.info { "Call to addArchive" }
        }
        Database.connect(postgresDatabase.dataSource)
        val result: MutableList<ArkivResponse> = mutableListOf()
        transaction {

            // Allow dokumentasjon to be any length - split it up if it exceeds column size
            val modelSplitToFit: MutableList<ArkivModel> = mutableListOf()
            requestBody.forEach { arkivModel ->
                if (arkivModel.dokumentasjon.length < DOKUMENTASJON_LENGTH) {
                    modelSplitToFit.add(arkivModel)
                } else {
                    arkivModel.dokumentasjon.chunked(DOKUMENTASJON_LENGTH).forEach {
                        modelSplitToFit.add(
                            ArkivModel(
                                opprettetAv = arkivModel.opprettetAv,
                                kilde = arkivModel.kilde,
                                dokumentasjonId = arkivModel.dokumentasjonId,
                                dokumentasjon = it,
                                dokumentdato = arkivModel.dokumentdato,
                                aktoerid = arkivModel.aktoerid,
                                fnr = arkivModel.fnr,
                                orgnr = arkivModel.orgnr,
                                tema = arkivModel.tema,
                                konfidentiellt = arkivModel.konfidentiellt
                            )
                        )
                    }
                }
            }

            if (modelSplitToFit.size > requestBody.size) {
                log.warn { "Request contained payload with dokumentasjon size larger the column size - has been split into several rows" }
            }

            modelSplitToFit.forEach { payload ->
                transaction {
                    var id = -1L
                    // val id = entryIdOfDokumentasjonId(payload.dokumentasjonId)
                    val update = false // id > -1 //TODO Only insert for now
                    log.info { "Will attempt ${if (update) "update" else "insert"}" }
                    val now = DateTime.now()
                    val resultId = if (update) {
                        ArkivV4.update({ ArkivV4.dokumentasjonId eq payload.dokumentasjonId }) {
                            it[dato] = now
                            it[opprettetAv] = payload.opprettetAv
                            it[kilde] = payload.kilde
                            it[dokumentasjon] = payload.dokumentasjon
                            it[dokumentasjonId] = payload.dokumentasjonId
                            it[dokumentdato] = DateTime.parse(payload.dokumentdato, fmt_onlyDay)
                            it[aktoerid] = payload.aktoerid
                            it[fnr] = payload.fnr
                            it[orgnr] = payload.orgnr
                            it[tema] = payload.tema
                            it[konfidentiellt] = payload.konfidentiellt
                        }; id
                    } else {
                        ArkivV4.insert {
                            it[dato] = now
                            it[opprettetAv] = payload.opprettetAv
                            it[kilde] = payload.kilde
                            it[dokumentasjon] = payload.dokumentasjon
                            it[dokumentasjonId] = payload.dokumentasjonId
                            it[dokumentdato] = DateTime.parse(payload.dokumentdato, fmt_onlyDay)
                            it[aktoerid] = payload.aktoerid
                            it[fnr] = payload.fnr
                            it[orgnr] = payload.orgnr
                            it[tema] = payload.tema
                            it[konfidentiellt] = payload.konfidentiellt
                        } get ArkivV4.id
                    }
                    if (update) {
                        log.info { "Made an update on dokumentasjonId: ${payload.dokumentasjonId}, entryid: $id" }
                    } else {
                        Metrics.latestId.set(resultId.toDouble())
                        log.info { "Inserted arkiv post (konfidentiellt ${payload.konfidentiellt}) and got resultId $resultId back" }
                    }

                    result.add(
                        ArkivResponse(
                            resultId,
                            fmt.print(now),
                            payload.opprettetAv,
                            payload.kilde,
                            payload.dokumentasjonId,
                            if (payload.konfidentiellt) "konfidentiellt" else summarize(payload.dokumentasjon),
                            fmt_onlyDay.print(DateTime.parse(payload.dokumentdato, fmt_onlyDay)),
                            if (payload.konfidentiellt) "-hidden-" else payload.aktoerid,
                            if (payload.konfidentiellt) "-hidden-" else payload.fnr,
                            if (payload.konfidentiellt) "-hidden-" else payload.orgnr,
                            if (payload.konfidentiellt) "-hidden-" else payload.tema,
                            payload.konfidentiellt
                        )
                    )
                }
            }
        }
        return result
    }

    fun Query.andWhere(andPart: SqlExpressionBuilder.() -> Op<Boolean>) = adjustWhere {
        val expr = Op.build { andPart() }
        if (this == null) expr
        else this and expr
    }

    fun henteArchive(henteRequest: HenteModel): List<HenteResponse> {
        if (isDev) log.info { "henteArchive henteRequest: $henteRequest (Log in dev)" }
        log.info { "Connecting to db for hente" }
        Database.connect(postgresDatabase.dataSource)
        var result: List<HenteResponse> = listOf()
        transaction {
            val query = ArkivV3.selectAll()
            if (henteRequest.id.isNotEmpty()) query.andWhere { ArkivV3.id eq henteRequest.id.toLong() }
            if (henteRequest.aktoerid.isNotEmpty()) query.andWhere { ArkivV3.aktoerid eq henteRequest.aktoerid }
            if (henteRequest.fnr.isNotEmpty()) query.andWhere { ArkivV3.fnr eq henteRequest.fnr }
            if (henteRequest.orgnr.isNotEmpty()) query.andWhere { ArkivV3.orgnr eq henteRequest.orgnr }
            if (henteRequest.tema.isNotEmpty()) query.andWhere { ArkivV3.tema eq henteRequest.tema }
            if (henteRequest.kilde.isNotEmpty()) query.andWhere { ArkivV3.kilde eq henteRequest.kilde }
            if (henteRequest.dokumentasjonId.isNotEmpty()) query.andWhere { ArkivV3.dokumentasjonId eq henteRequest.dokumentasjonId }
            if (henteRequest.dokumentdato.isNotEmpty()) query.andWhere {
                ArkivV3.dokumentdato eq DateTime.parse(
                    henteRequest.dokumentdato,
                    fmt_onlyDay
                )
            }
            query.andWhere { ArkivV3.konfidentiellt eq false }

            val resultRow = query.toList()
            File("/tmp/queryListHenteArchive").writeText(resultRow.toString())
            result = resultRow.map {
                HenteResponse(
                    id = it[ArkivV3.id],
                    dato = fmt.print(it[ArkivV3.dato]),
                    opprettetAv = it[ArkivV3.opprettetAv],
                    kilde = it[ArkivV3.kilde],
                    dokumentasjonId = it[ArkivV3.dokumentasjonId],
                    dokumentasjon = it[ArkivV3.dokumentasjon],
                    dokumentdato = fmt_onlyDay.print(it[ArkivV3.dokumentdato]),
                    aktoerid = it[ArkivV3.aktoerid],
                    fnr = it[ArkivV3.fnr],
                    orgnr = it[ArkivV3.orgnr],
                    tema = it[ArkivV3.tema]
                )
            }
        }
        log.info { "henteArchive returns ${result.size} entries" }
        return result.sortedBy { it.id }
    }

    fun henteArchiveV4(henteRequest: HenteModel): List<HenteResponse> {
        if (isDev) log.info { "henteArchive v4 henteRequest: $henteRequest (Log in dev)" }
        log.info { "Connecting to db for hente v4" }
        Database.connect(postgresDatabase.dataSource)
        var result: List<HenteResponse> = listOf()
        transaction {
            val query = ArkivV4.selectAll()
            if (henteRequest.id.isNotEmpty()) query.andWhere { ArkivV4.id eq henteRequest.id.toLong() }
            if (henteRequest.aktoerid.isNotEmpty()) query.andWhere { ArkivV4.aktoerid eq henteRequest.aktoerid }
            if (henteRequest.fnr.isNotEmpty()) query.andWhere { ArkivV4.fnr eq henteRequest.fnr }
            if (henteRequest.orgnr.isNotEmpty()) query.andWhere { ArkivV4.orgnr eq henteRequest.orgnr }
            if (henteRequest.tema.isNotEmpty()) query.andWhere { ArkivV4.tema eq henteRequest.tema }
            if (henteRequest.kilde.isNotEmpty()) query.andWhere { ArkivV4.kilde eq henteRequest.kilde }
            if (henteRequest.dokumentasjonId.isNotEmpty()) query.andWhere { ArkivV4.dokumentasjonId eq henteRequest.dokumentasjonId }
            if (henteRequest.dokumentdato.isNotEmpty()) query.andWhere {
                ArkivV4.dokumentdato eq DateTime.parse(
                    henteRequest.dokumentdato,
                    fmt_onlyDay
                )
            }
            query.andWhere { ArkivV4.konfidentiellt eq false }

            val resultRow = query.toList()
            File("/tmp/queryListHenteArchivev4").writeText(resultRow.toString())
            result = resultRow.map {
                HenteResponse(
                    id = it[ArkivV4.id],
                    dato = fmt.print(it[ArkivV4.dato]),
                    opprettetAv = it[ArkivV4.opprettetAv],
                    kilde = it[ArkivV4.kilde],
                    dokumentasjonId = it[ArkivV4.dokumentasjonId],
                    dokumentasjon = it[ArkivV4.dokumentasjon],
                    dokumentdato = fmt_onlyDay.print(it[ArkivV4.dokumentdato]),
                    aktoerid = it[ArkivV4.aktoerid],
                    fnr = it[ArkivV4.fnr],
                    orgnr = it[ArkivV4.orgnr],
                    tema = it[ArkivV4.tema]
                )
            }
        }
        log.info { "henteArchive v4 returns ${result.size} entries" }
        return result.sortedBy { it.id }
    }

    fun entryIdOfDokumentasjonId(dokumentasjonId: String): Int {
        Database.connect(postgresDatabase.dataSource)

        var result: Int = -1
        transaction {
            val statement = TransactionManager.current().connection.createStatement()

            log.info { "Attempt exist check" }
            val existsQuery = "SELECT id FROM arkivv3 WHERE arkivv3.\"dokumentasjonId\"='$dokumentasjonId'"

            val resultSet: ResultSet = statement.executeQuery(existsQuery)
            var exists = resultSet.next()
            result = if (exists) resultSet.getInt(1) else -1
        }
        return result
    }

    private const val cutOff = 30

    private fun summarize(dokumentasjon: String): String {
        return if (dokumentasjon.length > cutOff) {
            dokumentasjon.substring(0 until cutOff) + "... ($cutOff of ${dokumentasjon.length} characters)"
        } else {
            dokumentasjon
        }
    }

    private val fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
    val fmt_onlyDay = DateTimeFormat.forPattern("yyyy-MM-dd")
}
