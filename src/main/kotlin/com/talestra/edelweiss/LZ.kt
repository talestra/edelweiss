package com.talestra.edelweiss

import com.soywiz.korio.util.toUnsigned
import kotlin.math.max
import kotlin.math.min

// @TODO: HASH 16-bits + adjust searching
class LZ(val nbits: Int = 12, val minLen: Int = 2) {
    private val SIZE = (1 shl nbits)
    private val MASK = SIZE - 1
    var dataPos = 0; private set
    private val data = ByteArray(SIZE)

    private val last = IntArray(256) { -1 }
    private val prev = IntArray(SIZE) { -1 }

    data class Result(var pos: Int, var len: Int) {
        var nchecks: Int = 0
    }

    val result = Result(0, 0)

    fun put(v: Byte) = put(v.toUnsigned())

    fun put(v: Int) {
        val dpos = dataPos and MASK
        prev[dpos] = last[v]
        last[v] = dataPos
        data[dpos] = v.toByte()
        dataPos++
    }

    fun put(bytes: ByteArray, offset: Int = 0, len: Int = bytes.size - offset) {
        for (n in 0 until len) put(bytes[offset + n])
    }

    // @TODO: LZ overlapping
    private fun largestMatch(lp: Int, r: ByteArray, rp: Int, maxLen: Int): Int {
        for (n in 0 until maxLen) {
            //println("" + data[(lp + n) and MASK] + " : " + r[rp + n])
            if (data[(lp + n) and MASK] != r[rp + n]) return n
        }
        return maxLen
    }

    fun findLargest(check: ByteArray, offset: Int = 0, maxLen: Int = check.size - offset, minDist: Int = 0, maxChecks: Int = SIZE): Result {
        var ptr = last[check[offset].toUnsigned()]

        var foundPos = -1
        var foundLen = 0
        val maxPos = dataPos
        val minPos = max(0, dataPos - (SIZE - minDist))
        val rmaxLen = min(maxLen, check.size - offset)
        var nchecks = 0

        while (ptr >= minPos) {
            if (ptr < maxPos - minDist) {
                val len = largestMatch(ptr, check, offset, min(rmaxLen, maxPos - ptr))
                if (len > foundLen) {
                    foundPos = ptr
                    foundLen = len
                    nchecks++
                }
            }
            //println("ptr: $ptr --> $clen")
            ptr = prev[ptr and MASK]
            if (nchecks >= maxChecks) break
        }

        return result.apply {
            this.pos = foundPos
            this.len = foundLen
            this.nchecks = nchecks
        }
    }
}

//object LZ2 {
//    data class MatchResult(var pos: Int = 0, var len: Int = 0)
//
//    // @TODO: Improve performance by feeding data and maintaining a dictionary!
//    fun find_variable_match(s: UByteArraySlice, match: UByteArraySlice, min_dist: Int, mres: MatchResult = MatchResult()): MatchResult {
//        mres.pos = 0
//        mres.len = 0
//        val match_length = min(match.length, s.length)
//        //if (match.length > s.length) match.length = s.length
//        if ((s.length > 0) && (match_length > 0)) {
//            val iter_len = s.length - match_length - min_dist
//            for (n in 0 until iter_len) {
//                var m = 0
//                while (m < match_length) {
//                    //writefln("%d, %d", n, m);
//                    if (match[m] != s[n + m]) break
//                    m++
//                }
//                if (mres.len < m) {
//                    mres.len = m
//                    mres.pos = n
//                }
//            }
//            mres.pos = iter_len - mres.pos
//        }
//        return mres
//    }
//}