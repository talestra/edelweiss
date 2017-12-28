package com.talestra.edelweiss

import org.junit.Assert
import org.junit.Test

class LZTest {
    @Test
    fun test() {
        val lz = LZ()
        lz.put(byteArrayOf(4, 4, 4, 4, 4, 1, 2, 3, 4, 5, 6, 7, 1, 1, 1, 2, 3, 4))
        Assert.assertEquals(LZ.Result(pos = 5, len = 7), lz.findLargest(byteArrayOf(1, 2, 3, 4, 5, 6, 7)))
        Assert.assertEquals(LZ.Result(pos = 6, len = 6), lz.findLargest(byteArrayOf(1, 2, 3, 4, 5, 6, 7), 1))
        Assert.assertEquals(LZ.Result(pos = 6, len = 4), lz.findLargest(byteArrayOf(1, 2, 3, 4, 5, 6, 7), 1, 4))
        Assert.assertEquals(LZ.Result(pos = -1, len = 0), lz.findLargest(byteArrayOf(8, 8, 8, 8)))
    }

    @Test
    fun test2() {
        val lz = LZ()
        lz.put(byteArrayOf(4, 4, 4, 4, 4, 1, 2, 3, 4, 5, 6, 7, 1, 1, 1, 2, 3, 4) + ByteArray(0x4000) + byteArrayOf(1, 2, 3))
        Assert.assertEquals(LZ.Result(pos = 18 + 0x4000, len = 3), lz.findLargest(byteArrayOf(1, 2, 3, 4, 5, 6, 7)))
        Assert.assertEquals(LZ.Result(pos = -1, len = 0), lz.findLargest(byteArrayOf(8, 8, 8, 8)))
    }

    @Test
    fun name3() {
        val base = byteArrayOf(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        val lz = LZ()
        lz.put(base, 0, 6)
        Assert.assertEquals(LZ.Result(pos = 0, len = 1), lz.findLargest(base, 6))
    }

    @Test
    fun name4() {
        val base = byteArrayOf(1, 0, 2, 2, 2, 0, 0, 0, 0)
        val lz = LZ()
        lz.put(base, 0, 2)
        Assert.assertEquals(LZ.Result(pos = 1, len = 1), lz.findLargest(base, 5))
    }
}