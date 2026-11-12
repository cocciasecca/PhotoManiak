package app.inphotomaniak

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Immutable edits. Lamport clocks avoid relying on correctly set phone clocks. */
data class CollectionEdit(val id: String, val clock: Long, val kind: String, val target: String, val value: String, val member: String = "") {
    fun json(): JSONObject = JSONObject().put("id",id).put("clock",clock).put("kind",kind).put("target",target).put("value",value).put("member",member)
    fun validate() {
        require(isUuid(id) && clock in 1..(Long.MAX_VALUE-1)) { "Invalid collection edit" }
        when(kind) {
            "favorite", "archive" -> require(isFileId(target) && value in setOf("true","false") && member.isEmpty())
            "albumName" -> require(isUuid(target) && value.isNotBlank() && value.length <= 100 && member.isEmpty())
            "albumDeleted" -> require(isUuid(target) && value == "true" && member.isEmpty())
            "member" -> require(isUuid(target) && isFileId(member) && value in setOf("true","false"))
            else -> throw IOException("Unknown collection edit. Update PhotoManiak before syncing.")
        }
    }
    companion object {
        fun isUuid(value: String) = value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        fun isFileId(value: String) = value.matches(Regex("[1-9][0-9]{0,18}")) && value.toLongOrNull() != null
        fun parse(json: JSONObject) = CollectionEdit(json.getString("id"),json.getLong("clock"),json.getString("kind"),json.getString("target"),json.getString("value"),json.getString("member")).also { it.validate() }
    }
}

data class VirtualAlbum(val id: String, val name: String, val members: Set<String>)
data class Collections(val favorites: Set<String> = emptySet(), val archived: Set<String> = emptySet(), val albums: List<VirtualAlbum> = emptyList()) {
    companion object {
        fun from(edits: List<CollectionEdit>): Collections {
            val latest=edits.groupBy { Triple(it.kind,it.target,it.member) }.values.map { group -> group.maxWith(compareBy<CollectionEdit> { it.clock }.thenBy { it.id }) }
            fun flagged(kind: String) = latest.filter { it.kind==kind && it.value=="true" }.map { it.target }.toSet()
            val deleted=flagged("albumDeleted")
            val members=latest.filter { it.kind=="member" && it.value=="true" }.groupBy { it.target }
            return Collections(flagged("favorite"),flagged("archive"),latest.filter { it.kind=="albumName" && it.target !in deleted }.map {
                VirtualAlbum(it.target,it.value,members[it.target].orEmpty().map { edit -> edit.member }.toSet())
            }.sortedWith(compareBy<VirtualAlbum> { it.name.lowercase() }.thenBy { it.id }))
        }
    }
}

object CollectionJson {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_EDITS = 1000
    fun encode(drive: String, edits: List<CollectionEdit>): String {
        require(edits.size in 1..MAX_EDITS)
        edits.forEach { it.validate() }
        return JSONObject().put("format","PhotoManiak.collections").put("version",1).put("driveId",drive)
            .put("edits",JSONArray(edits.sortedBy { it.id }.map { it.json() })).toString().also { require(it.toByteArray().size <= MAX_BYTES) }
    }
    fun decode(text: String, drive: String): List<CollectionEdit> {
        require(text.toByteArray().size <= MAX_BYTES) { "Collection file is too large" }
        val json=JSONObject(text)
        require(json.getString("format")=="PhotoManiak.collections" && json.getInt("version")==1) { "Unsupported collection format. Update PhotoManiak before syncing." }
        require(json.getString("driveId")==drive) { "These collections belong to another kDrive" }
        val a=json.getJSONArray("edits")
        require(a.length() in 1..MAX_EDITS)
        return (0 until a.length()).map { CollectionEdit.parse(a.getJSONObject(it)) }.also { edits ->
            require(edits.map { it.id }.toSet().size == edits.size) { "Duplicate collection edit IDs" }
        }
    }
    fun filename() = "changes-${UUID.randomUUID()}.json"
    fun isFilename(name: String) = name.startsWith("changes-") && name.endsWith(".json") && CollectionEdit.isUuid(name.removePrefix("changes-").removeSuffix(".json"))
}

fun RemoteEntry.collectionId(): String = path.trimEnd('/').substringAfterLast('/').substringBefore('~')
fun GalleryState.visibleAssets(): List<Asset> = when(collectionView) {
    "favorites" -> assets.filter { it.entry.collectionId() in collections.favorites }
    "archive" -> assets.filter { it.entry.collectionId() in collections.archived }
    "album" -> assets.filter { it.entry.collectionId() in collections.albums.firstOrNull { a -> a.id==albumId }?.members.orEmpty() }
    else -> assets.filterNot { it.entry.collectionId() in collections.archived }
}
