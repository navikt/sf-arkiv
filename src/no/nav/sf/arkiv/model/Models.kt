package no.nav.sf.arkiv.model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

data class ArkivModel(
    var opprettetAv: String = "",
    var kilde: String = "",
    var dokumentasjonId: String = "",
    var dokumentasjon: String = "",
    var dokumentdato: String = "",
    var aktoerid: String = "",
    var fnr: String = "",
    var orgnr: String = "",
    var tema: String = "",
    var konfidentiellt: Boolean = false
)

data class HenteModel(
    val id: String = "",
    val kilde: String = "",
    val dokumentasjonId: String = "",
    val dokumentdato: String = "",
    val aktoerid: String = "",
    val fnr: String = "",
    val orgnr: String = "",
    val tema: String = ""
)

data class ArkivResponse(
    val id: Long,
    val dato: String,
    val opprettetAv: String,
    val kilde: String,
    val dokumentasjonId: String,
    val dokumentasjonSummarized: String,
    val dokumentdato: String,
    val aktoerid: String,
    val fnr: String,
    val orgnr: String,
    val tema: String,
    val konfidentiellt: Boolean
)

data class HenteResponse(
    val id: Long,
    val dato: String,
    val opprettetAv: String,
    val kilde: String,
    val dokumentasjonId: String,
    val dokumentasjon: String,
    val dokumentdato: String,
    val aktoerid: String,
    val fnr: String,
    val orgnr: String,
    val tema: String
)

fun ArkivModel.hasValidDokumentDato(): Boolean {
    if (this.dokumentdato.isNotEmpty()) {
        try {
            val dateParsed = DateTime.parse(this.dokumentdato, fmt_onlyDay)
        } catch (e: Exception) {
            return false
        }
    }
    return true
}

fun HenteModel.isEmpty(): Boolean {
    return id.isEmpty() && kilde.isEmpty() && dokumentasjonId.isEmpty() && dokumentdato.isEmpty() && aktoerid.isEmpty() && fnr.isEmpty() && orgnr.isEmpty() && tema.isEmpty()
}

fun HenteModel.hasValidDokumentDato(): Boolean {
    if (this.dokumentdato.isNotEmpty()) {
        try {
            val dateParsed = DateTime.parse(this.dokumentdato, fmt_onlyDay)
        } catch (e: Exception) {
            return false
        }
    }
    return true
}

val fmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
val fmt_onlyDay: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
