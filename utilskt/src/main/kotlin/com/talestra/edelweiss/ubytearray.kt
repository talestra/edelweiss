package com.talestra.edelweiss

class UByteArray(val array: ByteArray) {
    val size get() = array.size
    operator fun get(i: Int) = array[i].toInt() and 0xFF
    operator fun set(i: Int, v: Int) = run { array[i] = v.toByte() }
}
