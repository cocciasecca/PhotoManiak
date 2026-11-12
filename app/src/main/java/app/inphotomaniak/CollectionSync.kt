package app.inphotomaniak

interface CollectionStore {
    fun merge(edits: List<CollectionEdit>)
    fun received(id: String, etag: String): Boolean
    fun recordReceived(id: String, etag: String)
    fun nextBatch(drive: String): CollectionDb.Batch?
    fun acknowledge(batch: CollectionDb.Batch, drive: String)
}

/** Immutable files plus a durable outbox: retries never replace another device's data. */
class CollectionSync(private val client: DriveClient, private val db: CollectionStore) {
    fun run(folder: String, cancelled: ()->Unit = {}) {
        val drive=client.account.driveId
        var remote=client.list(folder)
        fun verify(batch: CollectionDb.Batch, entry: RemoteEntry) {
            val expected=CollectionJson.decode(batch.text,drive).associateBy { it.id }
            val actual=CollectionJson.decode(client.readCollection(entry),drive).associateBy { it.id }
            require(expected==actual) { "A collection filename is already in use with different contents. Your local changes are safe; sync stopped." }
        }
        for(entry in remote.filter { !it.directory && CollectionJson.isFilename(it.name) }) {
            cancelled()
            if(!db.received(entry.collectionId(),entry.etag)) {
                db.merge(CollectionJson.decode(client.readCollection(entry),drive))
                db.recordReceived(entry.collectionId(),entry.etag)
            }
        }
        while(true) {
            cancelled()
            val batch=db.nextBatch(drive) ?: break
            val existing=remote.filter { it.name==batch.name }
            require(existing.size <= 1) { "Duplicate collection filenames. Sync stopped." }
            if(existing.isNotEmpty()) verify(batch,existing.single())
            else {
                try { client.uploadCollection(folder,batch) }
                catch(e: Exception) {
                    // Cover timeouts after the server committed and exact-name conflict errors.
                    cancelled()
                    remote=client.list(folder)
                    val recovered=remote.singleOrNull { it.name==batch.name } ?: throw e
                    verify(batch,recovered)
                }
            }
            db.acknowledge(batch,drive)
        }
    }
}
