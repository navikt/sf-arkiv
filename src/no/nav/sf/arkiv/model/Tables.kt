package no.nav.sf.arkiv.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.datetime

const val DOKUMENTASJON_LENGTH = 131072

/**
 * Object that reflects the table Arkiv.
 * There is identical models ArkivV3 and ArkivV4 that contains older records. If you change the name of this
 * object you can access those.
 * In production:
 * ArkivV3 - 9219513 records created 2021-12-07T09:37 - 2023-04-21T12:34
 * ArkivV4 - 3148235 records created 2023-04-21T12:34 - 2024-10-09T09:19
 * Any newer records is found in current Arkiv
 */
object Arkiv : Table() {
    val id = long("id").autoIncrement().uniqueIndex()
    val dato = datetime("dato")
    val opprettetAv = varchar("opprettet_av", 50)
    val kilde = varchar("kilde", 43)
    val dokumentasjonId = varchar("dokumentasjonId", 20).index()
    val dokumentasjon = varchar("dokumentasjon", DOKUMENTASJON_LENGTH)
    val dokumentdato = datetime("dokumentdato")
    val aktoerid = varchar("aktoerid", 14).index()
    val fnr = varchar("fnr", 13).index()
    val orgnr = varchar("orgnr", 13)
    val tema = varchar("tema", 50)
    val konfidentiellt = bool("konfidentiellt")
}
