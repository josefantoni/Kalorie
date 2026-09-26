package antoni.kalorie.core.auth

class PendingMergeSnapshotStoreFake : PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    var stubbedSnapshot: PendingMergeSnapshot? = null
    var saveError: Exception? = null
    var deleteError: Exception? = null
    var deleteCallCount = 0
        private set

    // MARK: - Functions

    override fun save(snapshot: PendingMergeSnapshot) {
        saveError?.let { throw it }
        stubbedSnapshot = snapshot
    }

    override fun load(): PendingMergeSnapshot? = stubbedSnapshot

    override fun delete() {
        deleteCallCount += 1
        deleteError?.let { throw it }
        stubbedSnapshot = null
    }
}
