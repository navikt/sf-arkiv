package no.nav.nks.sf.arkiv.dokumentasjon.kafka

import java.io.File
import mu.KotlinLogging
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB.Companion.henteOffset
import no.nav.nks.sf.arkiv.dokumentasjon.database.DB.Companion.storeOffset
import no.nav.nks.sf.arkiv.dokumentasjon.kafkaJobInProgress
import no.nav.nks.sf.arkiv.dokumentasjon.kafkaJobResult
import no.nav.nks.sf.arkiv.dokumentasjon.toHenvendelseArkivModel
import no.nav.sf.library.AKafkaConsumer
import no.nav.sf.library.AnEnvironment
import no.nav.sf.library.EV_kafkaClientID
import no.nav.sf.library.KafkaConsumerStates
import no.nav.sf.library.PROGNAME
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer

data class WorkSettings(
    val kafkaGCPConfig: Map<String, Any> = AKafkaConsumer.configGCP + mapOf<String, Any>(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100
    ),
    val kafkaGCPConfigAlternative: Map<String, Any> = AKafkaConsumer.configGCP + mapOf<String, Any>(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.GROUP_ID_CONFIG to AnEnvironment.getEnvOrDefault(EV_kafkaClientID, PROGNAME) + "_init",
        ConsumerConfig.CLIENT_ID_CONFIG to AnEnvironment.getEnvOrDefault(EV_kafkaClientID, PROGNAME) + "_init",
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100
    )
)

private val log = KotlinLogging.logger {}

val ws = WorkSettings()

class Kafka {
    companion object {
        fun kafkaSample(): String {
            var result = ""
            var samplesLeft = 5
            val resultOK = AKafkaConsumer<String, String>(
                config = ws.kafkaGCPConfigAlternative,
                fromBeginning = true,
                topics = listOf("personoversikt.henvendelse-henvendelse")
            ).consume { cRecords ->

                if (cRecords.isEmpty) return@consume KafkaConsumerStates.IsFinished.also { log.info { "Solorun No more records found on topic" } }

                cRecords.map { Pair(it.value().toHenvendelseArkivModel(), it.offset()) }.forEach {
                    if (samplesLeft > 0) {
                        File("/tmp/sample").appendText("fnr ${it.first?.fnr}\n")
                        result += "fnr ${it.first?.fnr}\n"
                        samplesLeft--
                    }
                }

                if (samplesLeft > 0) KafkaConsumerStates.IsOk else KafkaConsumerStates.IsFinished
            }

            return result
        }
        suspend fun testKafkaRead() {
            log.info { "Start kafka read" }
            kafkaJobInProgress = true
            // val hashCodesCache: MutableList<Int> = mutableListOf()

            var failedParsed = 0

            var lastOffset = henteOffset() // TODO Load Offset at standard run

            var investigated = 0
            var stored = 0

            val resultOK = AKafkaConsumer<String, String>(
                config = ws.kafkaGCPConfig,
                fromBeginning = false,
                topics = listOf("personoversikt.henvendelse-henvendelse")
            ).consume { cRecords ->

                if (cRecords.isEmpty) return@consume KafkaConsumerStates.IsFinished.also { log.info { "Solorun No more records found on topic" } }

                val unprocessed = cRecords.filter { it.offset() > lastOffset }.map { investigated++; Pair(it.value().toHenvendelseArkivModel(), it.offset()) }.filter {
                    if (it.first == null) {
                        failedParsed++
                        false
                    } else {
                        true
                        // !hashCodesCache.contains(it.first.hashCode())
                    }
                }

                if (failedParsed > 0) {
                    log.error { "Failed at parsing topic value - will abort" }
                    return@consume KafkaConsumerStates.HasIssues
                }
                if (unprocessed.isEmpty()) return@consume KafkaConsumerStates.IsOk

                File("/tmp/investigate").appendText("${unprocessed.first().first?.fnr}\n")

                DB.addArchive(unprocessed.map { /*hashCodesCache.add(it.first.hashCode());*/ it.first!! }.toTypedArray())
                stored += unprocessed.count()
                lastOffset = unprocessed.last().second
                storeOffset(lastOffset)

                KafkaConsumerStates.IsOk
            }
            log.info { "Arkiv session done - investigated $investigated, stored $stored" }
            kafkaJobResult = "Arkiv session done - investigated $investigated, stored $stored"
            kafkaJobInProgress = false
        }
    }
}
