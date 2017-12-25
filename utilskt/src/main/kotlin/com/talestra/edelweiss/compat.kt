package com.talestra.edelweiss

import com.soywiz.kmem.ByteArrayBuffer
import com.soywiz.kmem.readS32_le
import com.soywiz.kmem.write32_le
import com.soywiz.korio.lang.ASCII
import com.soywiz.korio.lang.Charset
import com.soywiz.korio.lang.UTF8
import com.soywiz.korio.lang.toString
import com.soywiz.korio.stream.ByteArrayBuilder
import com.soywiz.korio.util.join
import java.io.File
import java.util.*

const val __TIMESTAMP__ = "UNKNOWN_DATE"

class UByteArray(val array: ByteArray) {
    constructor(count: Int) : this(ByteArray(count))
    val size get() = array.size
    operator fun get(i: Int) = array[i].toInt() and 0xFF
    operator fun set(i: Int, v: Int) = run { array[i] = v.toByte() }
}

fun ubyteArrayOf(vararg bytes: Int): UByteArray {
    val out = UByteArray(bytes.size)
    for (n in bytes.indices) out[n] = bytes[n]
    return out
}

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

class UByteArraySlice(private val array: UByteArray, start: Int, end: Int) : InternalArraySlice<Int>(array.size, start, end) {
    override fun getO(index: Int) = this[index]
    operator fun get(index: Int) = array[rindex(index)]
    operator fun set(index: Int, value: Int) = run { array[rindex(index)] = value }
    operator fun get(start: Int, end: Int) = UByteArraySlice(this.array, rindexb(start), rindexb(end))
    operator fun get(range: IntRange) = this[range.start, range.endInclusive + 1]
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

operator fun UByteArray.get(start: Int, end: Int) = UByteArraySlice(this, start, end)
operator fun ByteArray.get(start: Int, end: Int) = ByteArraySlice(this, start, end)
operator fun ShortArray.get(start: Int, end: Int) = ShortArraySlice(this, start, end)
operator fun IntArray.get(start: Int, end: Int) = IntArraySlice(this, start, end)
operator fun FloatArray.get(start: Int, end: Int) = FloatArraySlice(this, start, end)

operator fun UByteArray.get(range: IntRange) = UByteArraySlice(this, range.start, range.endInclusive + 1)
operator fun ByteArray.get(range: IntRange) = ByteArraySlice(this, range.start, range.endInclusive + 1)
operator fun ShortArray.get(range: IntRange) = ShortArraySlice(this, range.start, range.endInclusive + 1)
operator fun IntArray.get(range: IntRange) = IntArraySlice(this, range.start, range.endInclusive + 1)
operator fun FloatArray.get(range: IntRange) = FloatArraySlice(this, range.start, range.endInclusive + 1)

fun main(args: Array<String>) {
    println(byteArrayOf(1, 2, 3, 4, 5, 6)[1 until 4][0 until 1])
}


abstract class BasePtr<R : BasePtr<R, T>, T>(val esize: Int, var pos: Int) {
    abstract protected fun gen(pos: Int): R
    operator fun inc(): R = (this as R).apply { pos += esize }
    operator fun plus(offset: Int): R = gen(this.pos + offset * esize)
    operator fun minus(offset: Int): R = gen(this.pos - offset * esize)

    operator fun minus(that: BasePtr<R, T>): Int = (this.pos - that.pos) / esize

    fun get(): T = this[0]
    fun set(value: T): Unit = run { this[0] = value }
    abstract operator fun get(offset: Int): T
    abstract operator fun set(offset: Int, value: T): Unit
    operator fun compareTo(that: BasePtr<*, *>): Int = this.pos.compareTo(that.pos)
}

class BytePtr(val array: ByteArray, pos: Int = 0) : BasePtr<BytePtr, Byte>(1, pos) {
    override fun get(offset: Int): Byte = array[pos + offset]
    override fun set(offset: Int, value: Byte) = run { array[pos + offset] = value }
    override fun gen(pos: Int) = BytePtr(array, pos)
}

class IntPtr(val array: ByteArray, pos: Int = 0) : BasePtr<IntPtr, Int>(4, pos) {
    override fun get(offset: Int): Int = array.readS32_le(pos + offset * esize)
    override fun set(offset: Int, value: Int) = run { array.write32_le(pos + offset * esize, value) }
    override fun gen(pos: Int) = IntPtr(array, pos)
}

val UByteArray.ptr get() = BytePtr(this.array, 0)
val ByteArray.ptr get() = BytePtr(this, 0)
val BytePtr.int get() = IntPtr(this.array, 0)


fun format(fmt: String, vararg args: Any?) = fmt.format(*args)
fun printf(fmt: String, vararg args: Any?) = print(fmt.format(*args))

val UByteArray.length: Int get() = size
var <T> ArrayList<T>.length: Int
    get() = this.size
    set(nl: Int) {
        while (this.size > nl) this.removeAt(this.size - 1)
    }

val <V> List<V>.length: Int get() = this.size
val <K, V> Map<K, V>.length: Int get() = this.size

// @TODO:
abstract class Stream {
    var position: Long = 0L
    var length: Long = 0L
    var size: Long get() = length; set(value) = run { length = value }
    fun readBytes(len: Int): ByteArray = TODO()
    fun readString(len: Int): ByteArray = TODO()
    fun readString(len: Int, charset: Charset): String = readString(len).toString(charset)
    fun writefln(fmt: String, vararg args: Any?): Unit = TODO()
    fun writefln(): Unit = TODO()
    fun close(): Unit = TODO()
    val eof: Boolean get() = TODO()
    fun readLine(charset: Charset): String = TODO()
    fun write(data: UByteArray): Unit = TODO()
    fun write(data: ByteArray): Unit = TODO()
    fun writeString(s: ByteArray): Unit = TODO()
    fun writeString(s: String, charset: Charset = ASCII): Unit = TODO()
    fun writeByte(v: Byte): Unit = TODO()
    fun writeByte(v: Int): Unit = TODO()
    fun writeInt(v: Int): Unit = TODO()
    fun readByte(): Byte = TODO()
    fun readShort(): Short = TODO()
    fun readInt(): Int = TODO()
    fun readIntArray(count: Int): IntArray = IntArray(count) { readInt() }
    fun copyFrom(other: Stream): Unit = TODO()
}

enum class FileMode { In, OutNew }
class SliceStream(val s: Stream, val offset: Long, val end: Long = s.length) : Stream()
class MemoryStream : Stream()
class BufferedFile(val name: String, val mode: FileMode = FileMode.In) : Stream()

operator fun String.get(range: IntRange) = this.substring(range.start, range.endInclusive + 1)

fun String.strip() = this.trim()
fun String.stripl() = this.trimStart()
fun String.stripr() = this.trimEnd()
fun toStringz(str: String) = str + "\u0000"
fun BytePtr.toStringz(charset: Charset = UTF8): String = TODO()

class ShowHelpException(t: String = "") : Exception(t)

data class IntRef(var v: Int)
data class Ref<T>(var v: T)

class ByteArraybuff {
    val bb = ByteArrayBuilder()
    operator fun plusAssign(data: ByteArray) = run { bb.append(data) }
    operator fun plusAssign(data: UByteArray) = run { bb.append(data.array) }
    fun toByteArray() = bb.toByteArray()
}

fun rand() = Random().nextInt()

fun listdir(path: String) = File(path).list()
fun writefln() = println("")
fun writefln(fmt: String, vararg args: Any?) = println(fmt.format(*args))
fun writef(fmt: String, vararg args: Any?) = print(fmt.format(*args))

fun std_file_exists(name: String) = File(name).exists()
fun std_file_write(name: String, data: ByteArray) = File(name).writeBytes(data)
fun std_file_read(name: String) = File(name).readBytes()

operator fun <T> List<T>.get(range: IntRange) = this.subList(range.start, range.endInclusive + 1)