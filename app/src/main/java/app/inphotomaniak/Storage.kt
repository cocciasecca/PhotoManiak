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
    fun clear() { check(prefs.edit().clear().commit()) }
}
class AssetDb(context: Context): SQLiteOpenHelper(context, "gallery.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE assets(path TEXT PRIMARY KEY, name TEXT NOT NULL, mime TEXT NOT NULL, modified INTEGER NOT NULL, etag TEXT NOT NULL, captured INTEGER NOT NULL, day TEXT NOT NULL, source TEXT NOT NULL, metadata INTEGER NOT NULL)"); db.execSQL("CREATE INDEX timeline ON assets(captured DESC)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<Asset> = readableDatabase.rawQuery("SELECT * FROM assets ORDER BY captured DESC, path", null).use { c -> buildList {
        while(c.moveToNext()) add(Asset(RemoteEntry(c.getString(0),c.getString(1),false,c.getString(2),c.getLong(3),c.getString(4)),CaptureDate(c.getLong(5),c.getString(6),c.getString(7)),c.getInt(8)==1))
    } }
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
