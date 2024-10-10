package no.nav.sf.arkiv.database

import mu.KotlinLogging
import no.nav.sf.arkiv.Metrics
import no.nav.sf.arkiv.isDev
import no.nav.sf.arkiv.model.Arkiv
import no.nav.sf.arkiv.model.ArkivModel
import no.nav.sf.arkiv.model.ArkivResponse
import no.nav.sf.arkiv.model.DOKUMENTASJON_LENGTH
import no.nav.sf.arkiv.model.HenteModel
import no.nav.sf.arkiv.model.HenteResponse
import no.nav.sf.arkiv.model.fmt
import no.nav.sf.arkiv.model.fmt_onlyDay
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import java.io.File

private val log = KotlinLogging.logger { }

object DB {
    val postgresDatabase = PostgresDatabase()

    fun addArchive(requestBody: List<ArkivModel>): List<ArkivResponse> {
        if (isDev) {
            log.info { "Call to addArchive requestBody: ${requestBody.toList()} (Log in dev)" }
        } else {
            log.info { "Call to addArchive" }
        }
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
                        Arkiv.update({ Arkiv.dokumentasjonId eq payload.dokumentasjonId }) {
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
                        Arkiv.insert {
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
                        } get Arkiv.id
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

    fun henteArchiveV4(henteRequest: HenteModel): List<HenteResponse> {
        if (isDev) log.info { "henteArchive v4 henteRequest: $henteRequest (Log in dev)" }
        var result: List<HenteResponse> = listOf()
        transaction {
            val query = Arkiv.selectAll()
            if (henteRequest.id.isNotEmpty()) query.andWhere { Arkiv.id eq henteRequest.id.toLong() }
            if (henteRequest.aktoerid.isNotEmpty()) query.andWhere { Arkiv.aktoerid eq henteRequest.aktoerid }
            if (henteRequest.fnr.isNotEmpty()) query.andWhere { Arkiv.fnr eq henteRequest.fnr }
            if (henteRequest.orgnr.isNotEmpty()) query.andWhere { Arkiv.orgnr eq henteRequest.orgnr }
            if (henteRequest.tema.isNotEmpty()) query.andWhere { Arkiv.tema eq henteRequest.tema }
            if (henteRequest.kilde.isNotEmpty()) query.andWhere { Arkiv.kilde eq henteRequest.kilde }
            if (henteRequest.dokumentasjonId.isNotEmpty()) query.andWhere { Arkiv.dokumentasjonId eq henteRequest.dokumentasjonId }
            if (henteRequest.dokumentdato.isNotEmpty()) query.andWhere {
                Arkiv.dokumentdato eq DateTime.parse(
                    henteRequest.dokumentdato,
                    fmt_onlyDay
                )
            }
            query.andWhere { Arkiv.konfidentiellt eq false }

            val resultRow = query.toList()
            File("/tmp/queryListHenteArchivev4").writeText(resultRow.toString())
            result = resultRow.map {
                HenteResponse(
                    id = it[Arkiv.id],
                    dato = fmt.print(it[Arkiv.dato]),
                    opprettetAv = it[Arkiv.opprettetAv],
                    kilde = it[Arkiv.kilde],
                    dokumentasjonId = it[Arkiv.dokumentasjonId],
                    dokumentasjon = it[Arkiv.dokumentasjon],
                    dokumentdato = fmt_onlyDay.print(it[Arkiv.dokumentdato]),
                    aktoerid = it[Arkiv.aktoerid],
                    fnr = it[Arkiv.fnr],
                    orgnr = it[Arkiv.orgnr],
                    tema = it[Arkiv.tema]
                )
            }
        }
        log.info { "henteArchive v4 returns ${result.size} entries" }
        return result.sortedBy { it.id }
    }

    private const val cutOff = 30

    private fun summarize(dokumentasjon: String): String {
        return if (dokumentasjon.length > cutOff) {
            dokumentasjon.substring(0 until cutOff) + "... ($cutOff of ${dokumentasjon.length} characters)"
        } else {
            dokumentasjon
        }
    }

    fun listTables(): List<String> {
        val result: MutableList<String> = mutableListOf()
        transaction(postgresDatabase.databaseConnection) {
            log.info { "Tables:" }
            SchemaUtils.listTables().forEach {
                log.info { it }
                result.add(it.removePrefix("public."))
            }
            log.info { " - end tables" }
        }
        return result
    }
}
