package app.petdance.domain

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun parseServerTime(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    val text = raw.trim().replace(" ", "T")
    runCatching { return Instant.parse(text) }
    runCatching { return OffsetDateTime.parse(text).toInstant() }
    runCatching { return LocalDate.parse(text).atStartOfDay(ZoneId.of("UTC")).toInstant() }
    return null
}

fun formatWhen(raw: String?, now: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = parseServerTime(raw) ?: return raw.orEmpty()
    val zoned = instant.atZone(zone)
    val time = zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (zoned.toLocalDate()) {
        now -> "今天 $time"
        now.minusDays(1) -> "昨天 $time"
        else -> zoned.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }
}
