package com.talestra.edelweiss

import com.soywiz.korio.async.syncTest
import com.soywiz.korio.vfs.resourcesVfs
import org.junit.Assert

class Compression2Test {
    @org.junit.Test
    fun testDecompress() = syncTest {
        val compressed = resourcesVfs["007a6"].readAll()
        val expected = resourcesVfs["007a6.u"].readAll()
        val uncompressed = decompress(compressed)
        Assert.assertArrayEquals(expected, uncompressed)
        //File("007a6.u").writeBytes(uncompressed)
    }
}