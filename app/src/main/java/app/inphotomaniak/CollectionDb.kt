package app.inphotomaniak

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.util.UUID

/** Separate from the replaceable media index. Pending edits and upload bytes survive process death. */
class CollectionDb(context: Context): SQLiteOpenHelper(context,"collections.db",null,1), CollectionStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE edits(id TEXT PRIMARY KEY, json TEXT NOT NULL, pending INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE outbox(name TEXT PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE received(id TEXT PRIMARY KEY, etag TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun all(): List<CollectionEdit> = readableDatabase.rawQuery("SELECT json FROM edits",null).use { c -> buildList {
        while(c.moveToNext()) add(CollectionEdit.parse(JSONObject(c.getString(0))))
    } }
    @Synchronized fun snapshot() = Collections.from(all())
    @Synchronized fun pendingCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM edits WHERE pending=1",null).use { it.moveToFirst();it.getInt(0) }
    @Synchronized fun edit(kind: String, targets: List<String>, value: String, member: String = "") {
        var clock=all().maxOfOrNull { it.clock } ?: 0L
        val edits=targets.distinct().map { CollectionEdit(UUID.randomUUID().toString(),++clock,kind,it,value,member).also { e -> e.validate() } }
        transaction { edits.forEach { put(it,true) } }
    }
    @Synchronized fun members(album: String, files: List<String>, added: Boolean) {
        var clock=all().maxOfOrNull { it.clock } ?: 0L
        val edits=files.distinct().map { CollectionEdit(UUID.randomUUID().toString(),++clock,"member",album,added.toString(),it).also { e -> e.validate() } }
        transaction { edits.forEach { put(it,true) } }
    }
    private fun put(edit: CollectionEdit, pending: Boolean) {
        val existing=readableDatabase.rawQuery("SELECT json FROM edits WHERE id=?",arrayOf(edit.id)).use { if(it.moveToFirst()) CollectionEdit.parse(JSONObject(it.getString(0))) else null }
        if(existing != null) { require(existing==edit) { "A collection edit was modified remotely. Sync stopped." };return }
        check(writableDatabase.insertOrThrow("edits",null,ContentValues().apply { put("id",edit.id);put("json",edit.json().toString());put("pending",if(pending) 1 else 0) }) != -1L)
    }
    @Synchronized override fun merge(edits: List<CollectionEdit>) = transaction { edits.forEach { it.validate();put(it,false) } }
    @Synchronized override fun received(id: String, etag: String) = readableDatabase.rawQuery("SELECT etag FROM received WHERE id=?",arrayOf(id)).use { it.moveToFirst() && it.getString(0)==etag }
    @Synchronized override fun recordReceived(id: String, etag: String) { writableDatabase.insertWithOnConflict("received",null,ContentValues().apply { put("id",id);put("etag",etag) },SQLiteDatabase.CONFLICT_REPLACE) }
    data class Batch(val name: String,val text: String)
    @Synchronized override fun nextBatch(drive: String): Batch? {
        readableDatabase.rawQuery("SELECT name,json FROM outbox LIMIT 1",null).use { if(it.moveToFirst()) return Batch(it.getString(0),it.getString(1)) }
        val pending=readableDatabase.rawQuery("SELECT json FROM edits WHERE pending=1 ORDER BY id LIMIT ?",arrayOf(CollectionJson.MAX_EDITS.toString())).use { c -> buildList {
            while(c.moveToNext()) add(CollectionEdit.parse(JSONObject(c.getString(0))))
        } }
        if(pending.isEmpty()) return null
        return Batch(CollectionJson.filename(),CollectionJson.encode(drive,pending)).also { batch ->
            writableDatabase.insertOrThrow("outbox",null,ContentValues().apply { put("name",batch.name);put("json",batch.text) })
        }
    }
    @Synchronized override fun acknowledge(batch: Batch, drive: String) = transaction {
        CollectionJson.decode(batch.text,drive).forEach { edit -> writableDatabase.execSQL("UPDATE edits SET pending=0 WHERE id=?",arrayOf(edit.id)) }
        writableDatabase.delete("outbox","name=?",arrayOf(batch.name))
    }
    @Synchronized fun resetForLocation() = transaction {
        writableDatabase.execSQL("UPDATE edits SET pending=1")
        writableDatabase.delete("outbox",null,null)
        writableDatabase.delete("received",null,null)
    }
    @Synchronized fun clear() = transaction { listOf("edits","outbox","received").forEach { writableDatabase.delete(it,null,null) } }
    private fun transaction(block: ()->Unit) {
        val db=writableDatabase;db.beginTransaction()
        try { block();db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
}
