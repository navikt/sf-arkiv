import io.prometheus.client.Gauge

data class WMetrics(
    val requestArkiv: Gauge = Gauge
        .build()
        .name("request_arkiv")
        .help("request_arkiv")
        .register(),
    val requestHente: Gauge = Gauge
        .build()
        .name("request_hente")
        .help("request_hente")
        .register(),
    val insertedEntries: Gauge = Gauge
        .build()
        .name("inserted_entries")
        .help("inserted_entries")
        .register(),
    val issues: Gauge = Gauge
        .build()
        .name("issues")
        .help("issues")
        .register()
) {
    fun clearAll() {
        requestArkiv.clear()
        requestHente.clear()
        insertedEntries.clear()
        issues.clear()
    }
}

val workMetrics = WMetrics()
