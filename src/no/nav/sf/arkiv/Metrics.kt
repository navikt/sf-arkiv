package no.nav.sf.arkiv

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.hotspot.DefaultExports

object Metrics {
    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val requestArkiv = registerGauge("request_arkiv")
    val requestHente = registerGauge("request_hente")
    val insertedEntries = registerGauge("inserted_entries")
    val latestId = registerGauge("latest_id")
    val issues = registerGauge("issues")

    val arkivItem = registerLabelCounter("arkiv_item", "opprettet_av", "kilde", "tema", "konfidentiellt")

    fun registerLabelCounter(
        name: String,
        vararg labels: String,
    ) = Counter
        .build()
        .name(name)
        .help(name)
        .labelNames(*labels)
        .register()

    fun registerGauge(name: String): Gauge =
        Gauge
            .build()
            .name(name)
            .help(name)
            .register()

    init {
        DefaultExports.initialize()
    }
}
