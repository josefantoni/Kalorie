package antoni.kalorie.core.auth

class MergeStatusReportingFake : MergeStatusReporting {

    // MARK: - Properties

    var beginMergeCallCount = 0
        private set
    var endMergeCallCount = 0
        private set

    // MARK: - Functions

    override fun beginMerge() {
        beginMergeCallCount += 1
    }

    override fun endMerge() {
        endMergeCallCount += 1
    }
}
