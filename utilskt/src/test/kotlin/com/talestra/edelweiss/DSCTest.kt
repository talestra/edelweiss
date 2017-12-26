package com.talestra.edelweiss

import com.soywiz.korio.async.syncTest
import com.soywiz.korio.util.hex
import com.soywiz.korio.vfs.resourcesVfs
import org.junit.Assert

class DSCTest {
    fun testCompression(level: Int, input: ByteArray) {
        val compressed = DSC.compress(input, level = level)
        val uncompressed = DSC.decompress(compressed)
        if (!input.contentEquals(uncompressed)) {
            println("input:" + input.hex)
            println("uncompressed:" + uncompressed.hex)
            println("compressed:" + compressed.size)
        }
        Assert.assertArrayEquals(input, uncompressed)
    }

    fun testCompression(input: ByteArray) {
        testCompression(9, input)
        testCompression(0, input)
    }

    @org.junit.Test
    fun test1() = testCompression(byteArrayOf(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))

    @org.junit.Test
    fun test2() = testCompression(byteArrayOf(1, 1, 1))

    @org.junit.Test
    fun test2b() = testCompression(byteArrayOf(1, 1, 1, 1))

    @org.junit.Test
    fun test2c() = testCompression(byteArrayOf(1, 1, 1, 1, 1))

    @org.junit.Test
    fun test2d() = testCompression(byteArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1))

    @org.junit.Test
    fun test3() = testCompression(byteArrayOf(1))

    @org.junit.Test
    fun test4() = testCompression(byteArrayOf())

    @org.junit.Test
    fun testDecompress() = syncTest {
        val compressed = resourcesVfs["007a6"].readAll()
        val expected = resourcesVfs["007a6.u"].readAll()
        val uncompressed = DSC.decompress(compressed)
        Assert.assertArrayEquals(expected, uncompressed)
        Assert.assertArrayEquals(expected, DSC.decompress(DSC.compress(uncompressed, level = 9)))
        Assert.assertArrayEquals(expected, DSC.decompress(DSC.compress(uncompressed, level = 0)))
        //File("007a6.u").writeBytes(uncompressed)
    }
}