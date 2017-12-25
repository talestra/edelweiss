package com.talestra.edelweiss

abstract class InternalArraySlice<T>(arrayLength: Int, private val start: Int, private val end: Int) {
    init {
        if (start > end) throw IllegalArgumentException("start=$start > end=$end")
        if (start !in 0 until arrayLength) throw IllegalArgumentException("start=$start !in 0 until $arrayLength")
        if (end !in 0 until arrayLength) throw IllegalArgumentException("end=$end !in 0 until $arrayLength")
    }

    val length: Int get() = end - start

    protected fun rindex(index: Int): Int {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException("index=$index !in 0 until $length")
        return start + index
    }

    protected fun rindexb(index: Int): Int {
        if (index < 0 || index > length) throw IndexOutOfBoundsException("index=$index !in 0 until $length")
        return start + index
    }

    abstract protected fun getO(index: Int): T

    override fun toString(): String = "[" + (0 until length).map { getO(it) }.joinToString(", ") + "]"
}

class ByteArraySlice(private val array: ByteArray, start: Int, end: Int) : InternalArraySlice<Byte>(array.size, start, end) {
    override fun getO(index: Int) = this[index]
    operator fun get(index: Int) = array[rindex(index)]
    operator fun set(index: Int, value: Byte) = run { array[rindex(index)] = value }
    operator fun get(start: Int, end: Int) = ByteArraySlice(this.array, rindexb(start), rindexb(end))
    operator fun get(range: IntRange) = this[range.start, range.endInclusive + 1]
}

class ShortArraySlice(private val array: ShortArray, start: Int, end: Int) : InternalArraySlice<Short>(array.size, start, end) {
    override fun getO(index: Int) = this[index]
    operator fun get(index: Int) = array[rindex(index)]
    operator fun set(index: Int, value: Short) = run { array[rindex(index)] = value }
    operator fun get(start: Int, end: Int) = ShortArraySlice(this.array, rindexb(start), rindexb(end))
    operator fun get(range: IntRange) = this[range.start, range.endInclusive + 1]
}

class IntArraySlice(private val array: IntArray, start: Int, end: Int) : InternalArraySlice<Int>(array.size, start, end) {
    override fun getO(index: Int) = this[index]
    operator fun get(index: Int) = array[rindex(index)]
    operator fun set(index: Int, value: Int) = run { array[rindex(index)] = value }
    operator fun get(start: Int, end: Int) = IntArraySlice(this.array, rindexb(start), rindexb(end))
    operator fun get(range: IntRange) = this[range.start, range.endInclusive + 1]
}

class FloatArraySlice(private val array: FloatArray, start: Int, end: Int) : InternalArraySlice<Float>(array.size, start, end) {
    override fun getO(index: Int) = this[index]
    operator fun get(index: Int) = array[rindex(index)]
    operator fun set(index: Int, value: Float) = run { array[rindex(index)] = value }
    operator fun get(start: Int, end: Int) = FloatArraySlice(this.array, rindexb(start), rindexb(end))
    operator fun get(range: IntRange) = this[range.start, range.endInclusive + 1]
}

operator fun ByteArray.get(start: Int, end: Int) = ByteArraySlice(this, start, end)
operator fun ShortArray.get(start: Int, end: Int) = ShortArraySlice(this, start, end)
operator fun IntArray.get(start: Int, end: Int) = IntArraySlice(this, start, end)
operator fun FloatArray.get(start: Int, end: Int) = FloatArraySlice(this, start, end)

operator fun ByteArray.get(range: IntRange) = ByteArraySlice(this, range.start, range.endInclusive + 1)
operator fun ShortArray.get(range: IntRange) = ShortArraySlice(this, range.start, range.endInclusive + 1)
operator fun IntArray.get(range: IntRange) = IntArraySlice(this, range.start, range.endInclusive + 1)
operator fun FloatArray.get(range: IntRange) = FloatArraySlice(this, range.start, range.endInclusive + 1)

fun main(args: Array<String>) {
    println(byteArrayOf(1, 2, 3, 4, 5, 6)[1 until 4][0 until 1])
}