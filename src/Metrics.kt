import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.hotspot.DefaultExports

object Metrics {
    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val requestArkiv = registerGauge("request_arkiv")
    val requestHente = registerGauge("request_hente")
    val insertedEntries = registerGauge("inserted_entries")
    val latestId = registerGauge("latest_id")
    val issues = registerGauge("issues")

    fun registerGauge(name: String): Gauge {
        return Gauge.build().name(name).help(name).register()
    }

    init {
        DefaultExports.initialize()
    }
}
