package com.talestra.edelweiss

import com.soywiz.korio.lang.ASCII
import com.soywiz.korio.lang.toString
import com.soywiz.korio.util.hex

class CompressionTest {
    @org.junit.Test
    fun name() {
        val input = byteArrayOf(1, 2, 3, 4, 5, 6, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        val result = compress(input)
        val rr = decompress(result)
        //println(result.toString(ASCII))
        println(input.hex)
        println(rr.hex)
    }
}