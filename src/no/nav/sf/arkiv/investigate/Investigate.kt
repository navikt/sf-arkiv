package no.nav.arkiv.investigate

import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface Investigate {
    companion object {
        fun writeText(text: String, append: Boolean = true) {
            val timeStamp = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
                .withZone(ZoneOffset.systemDefault())
                .format(Instant.now())
            FileOutputStream("/tmp/investigate", append).bufferedWriter().use { writer ->
                writer.write("$timeStamp : $text \n")
            }
        }
    }
}
