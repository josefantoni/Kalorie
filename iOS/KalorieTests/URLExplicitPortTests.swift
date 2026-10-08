import XCTest
@testable import Kalorie

final class URLExplicitPortTests: XCTestCase {
    func test_removingExplicitPort_stripsFirebaseStoragePort() throws {
        let url = try XCTUnwrap(URL(string: "https://firebasestorage.googleapis.com:443/v0/b/bucket/o/submissionPhotos%2Fuid%2Fa.jpg?alt=media&token=t"))
        XCTAssertEqual(
            url.removingExplicitPort().absoluteString,
            "https://firebasestorage.googleapis.com/v0/b/bucket/o/submissionPhotos%2Fuid%2Fa.jpg?alt=media&token=t"
        )
    }

    func test_removingExplicitPort_leavesUrlWithoutPortUntouched() throws {
        let url = try XCTUnwrap(URL(string: "https://firebasestorage.googleapis.com/v0/b/bucket/o/a%2Fb.jpg?alt=media"))
        XCTAssertEqual(url.removingExplicitPort(), url)
    }
}
