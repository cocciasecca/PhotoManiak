package app.inphotomaniak

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

// Paths are virtual API-ID/name hierarchies, never filesystem paths or arbitrary URLs.
data class RemoteEntry(val path: String, val name: String, val directory: Boolean, val mime: String, val modified: Long, val etag: String) {
    val video get() = mime.startsWith("video/") || name.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp")
    val media get() = video || mime.startsWith("image/") || name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "avif", "dng")
}
data class CaptureDate(val millis: Long, val day: String, val source: String)
data class Asset(val entry: RemoteEntry, val date: CaptureDate, val metadataRead: Boolean)
data class FolderRules(val included: Set<String> = emptySet(), val excluded: Set<String> = emptySet()) {
    fun excluded(path: String) = excluded.any { contains(it, path) }
    fun accepts(path: String) = included.any { contains(it, path) } && !excluded(path)
    companion object { fun contains(folder: String, path: String) = path == folder || path.startsWith(folder.trimEnd('/') + "/") }
}
object Dates {
    val priority = listOf("SubSecDateTimeOriginal", "SubSecCreateDate", "DateTimeOriginal", "CreationDate", "CreateDate", "MediaCreateDate", "DateTimeCreated", "GPSDateTime", "DateTimeUTC", "SonyDateTime2", "SourceImageCreateTime")
    fun parse(value: String, source: String): CaptureDate? {
        val raw = value.trim().trimEnd('\u0000')
        val normalized = raw.replaceFirst(Regex("^(\\d{4}):(\\d{2}):(\\d{2})[ T]"), "$1-$2-$3T")
            .replaceFirst(Regex("^(\\d{4}-\\d{2}-\\d{2}) "), "$1T")
        val offset = runCatching { OffsetDateTime.parse(normalized) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSSX").withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
        if (offset != null) return CaptureDate(offset.toInstant().toEpochMilli(), offset.toLocalDate().toString(), source)
        val local = runCatching { LocalDateTime.parse(normalized) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.SSSX").withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
        return local?.let { CaptureDate(it.toInstant(ZoneOffset.UTC).toEpochMilli(), it.toLocalDate().toString(), source) }
    }
    fun resolve(tags: Map<String, String>, name: String, modified: Long): CaptureDate {
        priority.forEach { key -> tags[key]?.let { parse(it, key)?.let { result -> return result } } }
        // Require a complete date, never guess day/month order.
        val match = Regex("(?:^|[^0-9])(20\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})(?:[T_ -]?(\\d{2})[._:-]?(\\d{2})[._:-]?(\\d{2}))?(?:[^0-9]|$)").find(name)
        if (match != null) {
            val g = match.groupValues
            parse("${g[1]}-${g[2]}-${g[3]}T${g[4].ifEmpty { "00" }}:${g[5].ifEmpty { "00" }}:${g[6].ifEmpty { "00" }}", "Nome file")?.let { return it }
        }
        return CaptureDate(modified, if (modified > 0) Instant.ofEpochMilli(modified).atZone(ZoneOffset.UTC).toLocalDate().toString() else "", if (modified > 0) "Modifica remota" else "Data sconosciuta")
    }
}
