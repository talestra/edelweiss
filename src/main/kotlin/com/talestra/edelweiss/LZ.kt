package com.talestra.edelweiss

import kotlin.math.min

object LZ {
    class MatchResult(var pos: Int = 0, var len: Int = 0)

    // @TODO: Improve performance by feeding data and maintaining a dictionary!
    fun find_variable_match(s: UByteArraySlice, match: UByteArraySlice, mres: MatchResult, min_dist: Int = 0) {
        mres.pos = 0
        mres.len = 0
        val match_length = min(match.length, s.length)
        //if (match.length > s.length) match.length = s.length
        if ((s.length > 0) && (match_length > 0)) {
            val iter_len = s.length - match_length - min_dist
            for (n in 0 until iter_len) {
                var m = 0
                while (m < match_length) {
                    //writefln("%d, %d", n, m);
                    if (match[m] != s[n + m]) break
                    m++
                }
                if (mres.len < m) {
                    mres.len = m
                    mres.pos = n
                }
            }
            mres.pos = iter_len - mres.pos
        }
    }
}