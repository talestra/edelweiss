package com.talestra.edelweiss

import com.soywiz.korio.util.hex
import java.io.File

class CompressionTest {
    @org.junit.Test
    fun name() {
        val input = byteArrayOf(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        val compressed = compress(input)
        val uncompressed = decompress(compressed)
        File("compressed_kt").writeBytes(compressed)
        println(compressed.toList())
        //println(result.toString(ASCII))
        println(input.hex)
        println(uncompressed.hex)
    }
}