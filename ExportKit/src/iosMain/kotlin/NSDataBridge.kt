package antoni.kalorie.exportkit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
    }

fun renderXlsxData(report: Report): NSData = renderXlsx(report).toNSData()

fun renderPdfData(report: Report): NSData = renderPdf(report).toNSData()
