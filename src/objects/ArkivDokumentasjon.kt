package no.nav.nks.sf.arkiv.dokumentasjon.objects

import org.jetbrains.exposed.sql.Table

const val DOKUMENTASJON_LENGTH = 131072

/*
object Arkiv : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val dato = datetime("dato")
    val opprettetAv = varchar("opprettet_av", 50)
    val kilde = varchar("kilde", 43)
    val dokumentasjon = varchar("dokumentasjon", DOKUMENTASJON_LENGTH)
}
*/

object ArkivV3 : Table() {
    val id = integer("id").autoIncrement().primaryKey()
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

object OffsetStorage : Table() {
    val id = varchar("id", 50).primaryKey()
    val offset = integer("offset")
}
