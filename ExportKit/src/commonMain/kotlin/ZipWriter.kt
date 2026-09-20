package antoni.kalorie.exportkit

class ZipEntryInput(val name: String, val data: ByteArray)

private val crcTable = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

fun crc32(data: ByteArray): Int {
    var crc = -1
    for (byte in data) {
        crc = crcTable[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
    }
    return crc.inv()
}

private class ByteSink {
    private var buffer = ByteArray(1024)
    var size = 0
        private set

    fun write(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun u16(value: Int) {
        ensure(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value ushr 8).toByte()
    }

    fun u32(value: Int) {
        u16(value and 0xFFFF)
        u16(value ushr 16)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
        }
    }
}

private const val DOS_TIME = 0
private const val DOS_DATE = (1 shl 5) or 1

fun zipStored(entries: List<ZipEntryInput>): ByteArray {
    val sink = ByteSink()
    val offsets = mutableListOf<Int>()
    val crcs = entries.map { crc32(it.data) }

    entries.forEachIndexed { index, entry ->
        val name = entry.name.encodeToByteArray()
        offsets += sink.size
        sink.u32(0x04034b50)
        sink.u16(20)
        sink.u16(0x0800)
        sink.u16(0)
        sink.u16(DOS_TIME)
        sink.u16(DOS_DATE)
        sink.u32(crcs[index])
        sink.u32(entry.data.size)
        sink.u32(entry.data.size)
        sink.u16(name.size)
        sink.u16(0)
        sink.write(name)
        sink.write(entry.data)
    }

    val centralStart = sink.size
    entries.forEachIndexed { index, entry ->
        val name = entry.name.encodeToByteArray()
        sink.u32(0x02014b50)
        sink.u16(20)
        sink.u16(20)
        sink.u16(0x0800)
        sink.u16(0)
        sink.u16(DOS_TIME)
        sink.u16(DOS_DATE)
        sink.u32(crcs[index])
        sink.u32(entry.data.size)
        sink.u32(entry.data.size)
        sink.u16(name.size)
        sink.u16(0)
        sink.u16(0)
        sink.u16(0)
        sink.u16(0)
        sink.u32(0)
        sink.u32(offsets[index])
        sink.write(name)
    }
    val centralSize = sink.size - centralStart

    sink.u32(0x06054b50)
    sink.u16(0)
    sink.u16(0)
    sink.u16(entries.size)
    sink.u16(entries.size)
    sink.u32(centralSize)
    sink.u32(centralStart)
    sink.u16(0)
    return sink.toByteArray()
}
