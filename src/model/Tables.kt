package no.nav.nks.sf.arkiv.dokumentasjon.model

import org.jetbrains.exposed.sql.Table

const val DOKUMENTASJON_LENGTH = 131072

object ArkivV3 : Table() {
    val id = long("id").autoIncrement().primaryKey()
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

object ArkivV4 : Table() {
    val id = long("id").autoIncrement().primaryKey()
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
