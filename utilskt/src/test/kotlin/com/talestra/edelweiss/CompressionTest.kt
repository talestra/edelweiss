package com.talestra.edelweiss

import com.soywiz.korio.util.hex
import org.junit.Assert

class CompressionTest {
    fun testCompression(level: Int, input: ByteArray) {
        val compressed = compress(input, level = level)
        val uncompressed = decompress(compressed)
        if (!input.contentEquals(uncompressed)) {
            println("input:" + input.hex)
            println("uncompressed:" + uncompressed.hex)
            println("compressed:" + compressed.size)
        }
        Assert.assertArrayEquals(input, uncompressed)
    }

    @org.junit.Test
    fun test1() = testCompression(9, byteArrayOf(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))

    @org.junit.Test
    fun test2() = testCompression(9, byteArrayOf(1, 1, 1))

    @org.junit.Test
    fun test2b() = testCompression(9, byteArrayOf(1, 1, 1, 1))

    @org.junit.Test
    fun test2c() = testCompression(9, byteArrayOf(1, 1, 1, 1, 1))

    @org.junit.Test
    fun test2d() = testCompression(9, byteArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))

    @org.junit.Test
    fun test3() = testCompression(9, byteArrayOf(1))

    @org.junit.Test
    fun test4() = testCompression(9, byteArrayOf())
}