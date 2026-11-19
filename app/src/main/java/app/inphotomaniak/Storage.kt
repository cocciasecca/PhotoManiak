package app.inphotomaniak

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import org.json.JSONObject
import org.json.JSONArray

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("kdrive", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("kdrive", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun account(): Account? {
        val blob = prefs.getString("account", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12))) }
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8))
            Account(json.getString("drive"), json.getString("token"))
        }.getOrNull()
    }
    fun save(account: Account) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = JSONObject().put("drive", account.driveId).put("token", account.token).toString().toByteArray()
        check(prefs.edit().putString("account", Base64.encodeToString(cipher.iv + cipher.doFinal(data), Base64.NO_WRAP)).commit())
    }
    fun rules(): FolderRules {
        fun read(key: String): Set<String> = JSONArray(prefs.getString(key, "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        return FolderRules(read("included"), read("excluded"))
    }
    fun save(rules: FolderRules) { check(prefs.edit().putString("included", JSONArray(rules.included.toList()).toString()).putString("excluded", JSONArray(rules.excluded.toList()).toString()).commit()) }
    fun thumbnailSize(): ThumbnailSize = ThumbnailSize.entries.firstOrNull { it.name == prefs.getString("thumbnail_size", null) } ?: ThumbnailSize.MEDIUM
    fun save(size: ThumbnailSize) { check(prefs.edit().putString("thumbnail_size", size.name).commit()) }
    fun themeMode(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme_mode",null) } ?: ThemeMode.SYSTEM
    fun save(mode: ThemeMode) { check(prefs.edit().putString("theme_mode",mode.name).commit()) }
    fun colorPalette(): ColorPalette = ColorPalette.entries.firstOrNull { it.name==prefs.getString("color_palette",null) } ?: ColorPalette.FOREST
    fun customColor(): String = normalizeColor(prefs.getString("custom_color",DEFAULT_CUSTOM_COLOR).orEmpty()) ?: DEFAULT_CUSTOM_COLOR
    fun save(palette: ColorPalette, customColor: String) {
        val hex=requireNotNull(normalizeColor(customColor))
        check(prefs.edit().putString("color_palette",palette.name).putString("custom_color",hex).commit())
    }
    fun syncFolder(): String? = prefs.getString("collection_folder",null)
    fun syncFolder(path: String?) { check(prefs.edit().putString("collection_folder",path).commit()) }
    fun lastCollectionSync(): Long = prefs.getLong("collection_sync",0)
    fun lastCollectionSync(time: Long) { check(prefs.edit().putLong("collection_sync",time).commit()) }
    fun initialScanComplete(legacyComplete: Boolean = false): Boolean = prefs.getBoolean("initial_scan_complete",legacyComplete)
    fun saveInitialScanComplete(value: Boolean) { check(prefs.edit().putBoolean("initial_scan_complete",value).commit()) }
    fun scanStopped(): Boolean = prefs.getBoolean("scan_stopped",false)
    fun scanStopped(value: Boolean) { check(prefs.edit().putBoolean("scan_stopped",value).commit()) }
    fun clear() { check(prefs.edit().clear().commit()) }
}
internal class AssetDb(context: Context): SQLiteOpenHelper(context, "gallery.db", null, 2), ScanStore {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE assets(path TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, modified INTEGER NOT NULL, etag TEXT NOT NULL, captured INTEGER NOT NULL, day TEXT NOT NULL, source TEXT NOT NULL, metadata INTEGER NOT NULL)"); db.execSQL("CREATE INDEX timeline ON assets(captured DESC)"); createScanTables(db) }
    private fun createScanTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE scan_state(id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE scan_entries(path TEXT PRIMARY KEY, data TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if(oldVersion<2) createScanTables(db) }
    override fun checkpoint(): ScanCheckpoint? = readableDatabase.rawQuery("SELECT data FROM scan_state WHERE id=1",null).use { if(it.moveToFirst()) ScanCheckpoint.parse(it.getString(0)) else null }
    override fun staged(): List<RemoteEntry> = readableDatabase.rawQuery("SELECT data FROM scan_entries",null).use { c -> buildList {
        while(c.moveToNext()) { val j=JSONObject(c.getString(0));add(RemoteEntry(j.getString("path"),j.getString("name"),false,j.getString("mime"),j.getLong("modified"),j.getString("etag"))) }
    } }
    override fun indexed(): List<Asset> = all()
    override fun clearScan() {
        val db=writableDatabase;db.beginTransaction()
        try { db.delete("scan_state",null,null);db.delete("scan_entries",null,null);db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    override fun saveScan(state: ScanCheckpoint, entries: List<RemoteEntry>, reset: Boolean, replacement: List<Asset>?, updated: Asset?) {
        val db=writableDatabase;db.beginTransaction()
        try {
            if(reset) { db.delete("scan_state",null,null);db.delete("scan_entries",null,null) }
            entries.forEach { e ->
                val json=JSONObject().put("path",e.path).put("name",e.name).put("mime",e.mime).put("modified",e.modified).put("etag",e.etag)
                check(db.insertWithOnConflict("scan_entries",null,ContentValues().apply { put("path",e.path);put("data",json.toString()) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
            }
            replacement?.let { replace(it) }
            updated?.let { upsert(listOf(it)) }
            check(db.insertWithOnConflict("scan_state",null,ContentValues().apply { put("id",1);put("data",state.json()) },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun all(): List<Asset> = readableDatabase.rawQuery("SELECT * FROM assets ORDER BY captured DESC, path", null).use { c -> buildList {
        while(c.moveToNext()) add(Asset(RemoteEntry(c.getString(0),c.getString(1),false,c.getString(2),c.getLong(3),c.getString(4)),CaptureDate(c.getLong(5),c.getString(6),c.getString(7)),c.getInt(8)==1))
    } }
    fun remove(path: String) { writableDatabase.delete("assets", "path = ?", arrayOf(path)) }
    fun replace(assets: List<Asset>) = write(assets, clear=true)
    fun upsert(assets: List<Asset>) = write(assets, clear=false)
    private fun write(assets: List<Asset>, clear: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if(clear) db.delete("assets", null, null)
            assets.forEach { a -> db.insertWithOnConflict("assets",null,ContentValues().apply {
                put("path",a.entry.path);put("name",a.entry.name);put("mime",a.entry.mime);put("modified",a.entry.modified);put("etag",a.entry.etag)
                put("captured",a.date.millis);put("day",a.date.day);put("source",a.date.source);put("metadata",if(a.metadataRead) 1 else 0)
            },SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Could not save the local index" } } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
