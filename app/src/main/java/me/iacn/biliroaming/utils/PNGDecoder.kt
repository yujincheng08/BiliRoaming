package me.iacn.biliroaming.utils

import java.io.*
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Inflater

class PNGDecoder(private val input: InputStream) {
    private val crc = CRC32()
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    private var chunkLength = 0
    private var chunkType = 0
    private var chunkRemaining = 0

    var width = 0
        private set
    var height = 0
        private set
    var bitDepth = 0
        private set

    private var colorType = 0
    private var bytesPerPixel = 0

    init {
        readFully(buffer, 0, signature.size)
        if (!checkSignature(buffer))
            throw IOException("Not a valid PNG file")
        openChunk(IHDR)
        readIHDR()
        closeChunk()
        while (true) {
            openChunk()
            if (chunkType == IDAT)
                break
            closeChunk()
        }
    }

    fun decode(): ByteArray {
        val size = width * height * bytesPerPixel
        val buffer = ByteBuffer.allocate(size)
        val lineSize = (width * bitDepth + 7) / 8 * bytesPerPixel
        var curLine = ByteArray(lineSize + 1)
        var prevLine = ByteArray(lineSize + 1)
        val inflater = Inflater()
        try {
            for (y in 0 until height) {
                readChunkUnzip(inflater, curLine, length = curLine.size)
                unFilter(curLine, prevLine)
                buffer.position(y * width * bytesPerPixel)
                copy(buffer, curLine)
                val tmp = curLine
                curLine = prevLine
                prevLine = tmp
            }
        } finally {
            inflater.end()
        }
        val data = ByteArray(size)
        buffer.rewind()
        buffer.get(data)
        return data
    }

    private fun copy(buffer: ByteBuffer, curLine: ByteArray) {
        buffer.put(curLine, 1, curLine.size - 1)
    }

    private fun unFilter(curLine: ByteArray, prevLine: ByteArray) {
        when (curLine[0].toInt()) {
            0 -> {}
            1 -> unFilterSub(curLine)
            2 -> unFilterUp(curLine, prevLine)
            3 -> unFilterAverage(curLine, prevLine)
            4 -> unFilterPaeth(curLine, prevLine)
            else -> throw IOException("invalid filter type in scanline: " + curLine[0])
        }
    }

    private fun unFilterSub(curLine: ByteArray) {
        val bpp = bytesPerPixel
        var i = bpp + 1
        val n = curLine.size
        while (i < n) {
            curLine[i] = (curLine[i] + curLine[i - bpp]).toByte()
            ++i
        }
    }

    private fun unFilterUp(curLine: ByteArray, prevLine: ByteArray) {
        var i = 1
        val n = curLine.size
        while (i < n) {
            curLine[i] = (curLine[i] + prevLine[i]).toByte()
            ++i
        }
    }

    private fun unFilterAverage(curLine: ByteArray, prevLine: ByteArray) {
        val bpp = bytesPerPixel
        var i = 1
        while (i <= bpp) {
            curLine[i] = (curLine[i] + ((prevLine[i] and 0xFF) ushr 1)).toByte()
            ++i
        }
        val n = curLine.size
        while (i < n) {
            curLine[i] =
                (curLine[i] + (((prevLine[i] and 0xFF) + (curLine[i - bpp] and 0xFF)) ushr 1)).toByte()
            ++i
        }
    }

    private fun unFilterPaeth(curLine: ByteArray, prevLine: ByteArray) {
        val bpp = bytesPerPixel
        var i = 1
        while (i <= bpp) {
            curLine[i] = (curLine[i] + prevLine[i]).toByte()
            ++i
        }
        val n = curLine.size
        while (i < n) {
            val a = curLine[i - bpp] and 0xFF
            val b = prevLine[i] and 0xFF
            var c = prevLine[i - bpp] and 0xFF
            val p = a + b - c
            var pa = p - a
            if (pa < 0) pa = -pa
            var pb = p - b
            if (pb < 0) pb = -pb
            var pc = p - c
            if (pc < 0) pc = -pc
            if (pa <= pb && pa <= pc)
                c = a
            else if (pb <= pc)
                c = b
            curLine[i] = (curLine[i] + c).toByte()
            ++i
        }
    }

    private fun readIHDR() {
        if (chunkLength != 13)
            throw IOException("Chunk has wrong size")
        readChunk(buffer, length = 13)
        width = buffer.readInt(0)
        height = buffer.readInt(4)
        bitDepth = buffer[8] and 0xFF
        colorType = buffer[9] and 0xFF
        bytesPerPixel = when (colorType) {
            COLOR_GREYSCALE -> {
                if (bitDepth != 8) throw IOException("Unsupported bit depth: $bitDepth")
                1
            }
            COLOR_GREYALPHA -> {
                if (bitDepth != 8) throw IOException("Unsupported bit depth: $bitDepth")
                2
            }
            COLOR_TRUECOLOR -> {
                if (bitDepth != 8) throw IOException("Unsupported bit depth: $bitDepth")
                3
            }
            COLOR_TRUEALPHA -> {
                if (bitDepth != 8) throw IOException("Unsupported bit depth: $bitDepth")
                4
            }
            COLOR_INDEXED -> when (bitDepth) {
                8, 4, 2, 1 -> 1
                else -> throw IOException("Unsupported bit depth: $bitDepth")
            }
            else -> throw IOException("unsupported color format: $colorType")
        }
        if (buffer[10].toInt() != 0)
            throw IOException("unsupported compression method")
        if (buffer[11].toInt() != 0)
            throw IOException("unsupported filtering method")
        if (buffer[12].toInt() != 0)
            throw IOException("unsupported interlace method")
    }

    private fun closeChunk() {
        if (chunkRemaining > 0) {
            // just skip the rest and the CRC
            skip((chunkRemaining + 4).toLong())
        } else {
            readFully(buffer, 0, 4)
            val expectedCrc = buffer.readInt()
            val computedCrc = crc.value.toInt()
            if (computedCrc != expectedCrc)
                throw IOException("Invalid CRC")
        }
        chunkRemaining = 0
        chunkLength = 0
        chunkType = 0
    }

    private fun openChunk() {
        readFully(buffer, 0, 8)
        chunkLength = buffer.readInt(0)
        chunkType = buffer.readInt(4)
        chunkRemaining = chunkLength
        crc.reset()
        crc.update(buffer, 4, 4) // only chunkType
    }

    private fun openChunk(expected: Int) {
        openChunk()
        if (chunkType != expected)
            throw IOException("Expected chunk: " + Integer.toHexString(expected))
    }

    private fun readChunk(buffer: ByteArray, offset: Int = 0, length: Int): Int {
        val l = length.coerceAtMost(chunkRemaining)
        readFully(buffer, offset, l)
        crc.update(buffer, offset, l)
        chunkRemaining -= l
        return l
    }

    private fun refillInflater(inflater: Inflater) {
        while (chunkRemaining == 0) {
            closeChunk()
            openChunk(IDAT)
        }
        val read = readChunk(buffer, length = buffer.size)
        inflater.setInput(buffer, 0, read)
    }

    private fun readChunkUnzip(
        inflater: Inflater,
        buffer: ByteArray,
        offset: Int = 0,
        length: Int
    ) {
        var o = offset
        var l = length
        do {
            val read = inflater.inflate(buffer, o, l)
            if (read <= 0) {
                if (inflater.finished())
                    throw EOFException()
                if (inflater.needsInput()) {
                    refillInflater(inflater)
                } else {
                    throw IOException("Can't inflate $l bytes")
                }
            } else {
                o += read
                l -= read
            }
        } while (l > 0)
    }

    private fun readFully(buffer: ByteArray, offset: Int, length: Int) {
        var o = offset
        var l = length
        do {
            val read = input.read(buffer, o, l)
            if (read < 0) throw EOFException()
            o += read
            l -= read
        } while (l > 0)
    }

    private fun skip(amount: Long) {
        var tempAmount = amount
        while (tempAmount > 0) {
            val skipped = input.skip(tempAmount)
            tempAmount -= skipped
        }
    }

    companion object {
        private val signature = byteArrayOf(
            0x89.toByte(),
            0x50, // 'P'
            0x4E, // 'N'
            0x47, // 'G'
            0x0D, // '\r'
            0x0A, // '\n'
            0x1A,
            0x0A, // '\n'
        )

        private const val IHDR = 0x49484452
        private const val IDAT = 0x49444154

        private const val COLOR_GREYSCALE = 0
        private const val COLOR_TRUECOLOR = 2
        private const val COLOR_INDEXED = 3
        private const val COLOR_GREYALPHA = 4
        private const val COLOR_TRUEALPHA = 6

        private fun checkSignature(buffer: ByteArray): Boolean {
            for (i in signature.indices)
                if (buffer[i] != signature[i])
                    return false
            return true
        }
    }
}
