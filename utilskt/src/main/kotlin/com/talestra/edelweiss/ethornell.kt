package com.talestra.edelweiss

import com.soywiz.kmem.write32_le
import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.color.RGB
import com.soywiz.korim.color.RGBA
import com.soywiz.korim.format.TGA
import com.soywiz.korio.Korio
import com.soywiz.korio.error.ignoreErrors
import com.soywiz.korio.lang.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.getu
import com.soywiz.korio.util.toInt
import com.soywiz.korio.util.toUnsigned
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

// This program is released AS IT IS. Without any warranty and responsibility from the author.

fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell") + args) }

// Version of the utility.
const private val _version = "0.3"

// Utility macros.
private fun HIWORD(v: Int): Int = (v ushr 16) and 0xFFFF
private fun LOWORD(v: Int): Int = (v and 0xFFFF)
private fun HIBYTE(v: Int): Int = (v ushr 8) and 0xFF
private fun LOBYTE(v: Int): Int = (v and 0xFF)

// Utility functin for the decrypting.
private fun hash_update(hash_val: IntRef): Int {
    var eax: Int
    var ebx: Int
    var edx: Int
    var esi: Int
    var edi: Int
    //writefln("V:%08X", hash_val);
    // @TODO: Check if this multiplication is unsigned?
    edx = (20021 * LOWORD(hash_val.v))
    eax = (20021 * HIWORD(hash_val.v)) + (346 * hash_val.v) + HIWORD(edx)
    hash_val.v = (LOWORD(eax) shl 16) + LOWORD(edx) + 1
    //writefln("D:%08X", edx);
    //writefln("A:%08X", eax);
    return eax and 0x7FFF
}

// Read a variable value from a pointer.
private fun readVariable(data: ByteArray, ptr: IntRef): Int {
    var c = 0
    var v = 0
    var shift = 0
    do {
        c = data.getu(ptr.v)
        ptr.v++
        v = v or ((c and 0x7F) shl shift)
        shift += 7
    } while ((c and 0x80) != 0)
    return v
}

// Class to have read access to ARC files.
class ARC : Iterable<ARC.Entry> {
    val s: SyncStream
    val sd: SyncStream
    val table = arrayListOf<Entry>()
    val table_lookup = LinkedHashMap<String, Entry>()

    // Entry for the header.
    class Entry(
            val nameBytes: ByteArray = ByteArray(0x10), // Stringz with the name of the file.
            val start: Int = 0, // Slice of the file.
            val len: Int = 0
    ) {
        lateinit var arc: ARC

        companion object {
            fun read(s: SyncStream): Entry = s.run {
                Entry(
                        readBytes(0x10),
                        readS32_le(),
                        readS32_le()
                ).apply {
                    readS32_le() // pad
                    readS32_le() // pad
                }
            }
        }


        // Obtaining the processed name as a String.
        val name: String get() = nameBytes.openSync().readStringz()

        override fun toString(): String = "%-16s (%08X-%08X)".format(name, start, len)

        // Open a read-only stream for the file.
        fun open(): SyncStream = arc.open(this)

        // Method to save this entry to a file.
        fun save(name: String = this.name) {
            val s = MemorySyncStream()
            open().copyTo(s)
            s.close()
            File(name).writeBytes(s.toByteArray())
        }

        // Defines the explicit cast to Stream.
        //Stream opCast() { return open; }
    }

    // Check the struct to have the expected size.
    //static assert(Entry.sizeof == 0x20, "Invalid size for ARC.Entry");

    // Open a ARC using an stream.
    constructor(s: SyncStream, name: String = "unknwon") {
        this.s = s

        // Check the magic.
        assert(s.readString(12, LATIN1) == "PackFile    ") { format("It doesn't seems to be an ARC file ('%s')", name) }

        // Read the size.
        val table_length = s.readS32_le()

        table += (0 until table_length).map { Entry.read(s) }

        // Stre a SliceStream starting with the data part.
        sd = s.sliceWithStart(s.position)

        // Iterates over all the entries, creating references to this class, and creating a lookup table.
        for (n in 0 until table.length) {
            table_lookup[table[n].name] = table[n]
            table[n].arc = this
        }
    }

    // Open an ARC using a file name.
    constructor(name: String) : this(File(name).readBytes().openSync(), name)

    // Gets a read-only stream for a entry.
    fun open(e: Entry): SyncStream = sd.sliceWithBounds(e.start.toLong(), (e.start + e.len).toLong())

    // Defines an iterator for this class.
    override fun iterator(): Iterator<Entry> = table.iterator()

    // Defines an array accessor to obtain an entry file.
    operator fun get(name: String): Entry = table_lookup[name] ?: throw Exception(format("Unknown index '%s'", name))
}

// A color RGBA struct that defines methods to sum colors per component and to obtain average colors.
// @TODO: Move to Korim
fun RGBA.add(l: Int, r: Int): Int = RGBA.pack(
        RGBA.getR(l) + RGBA.getR(r),
        RGBA.getG(l) + RGBA.getG(r),
        RGBA.getB(l) + RGBA.getB(r),
        RGBA.getA(l) + RGBA.getA(r)
)

fun RGBA.sub(l: Int, r: Int): Int = RGBA.pack(
        RGBA.getR(l) - RGBA.getR(r),
        RGBA.getG(l) - RGBA.getG(r),
        RGBA.getB(l) - RGBA.getB(r),
        RGBA.getA(l) - RGBA.getA(r)
)

fun RGBA.avg(vararg colors: Int): Int = RGBA.pack(
        colors.sumBy { RGBA.getR(it) } / colors.size,
        colors.sumBy { RGBA.getG(it) } / colors.size,
        colors.sumBy { RGBA.getB(it) } / colors.size,
        colors.sumBy { RGBA.getA(it) } / colors.size
)

//class Color(var v: Int = 0) {
//    var r: Int get() = RGBA.getFastR(v); set(r) = run { v = RGBA.pack(r, g, b, a) }
//    var g: Int get() = RGBA.getFastG(v); set(g) = run { v = RGBA.pack(r, g, b, a) }
//    var b: Int get() = RGBA.getFastB(v); set(b) = run { v = RGBA.pack(r, g, b, a) }
//    var a: Int get() = RGBA.getFastA(v); set(a) = run { v = RGBA.pack(r, g, b, a) }
//
//    constructor(r: Int, g: Int, b: Int, a: Int) : this(RGBA.pack(r, g, b, a))
//    constructor(r: Byte, g: Byte, b: Byte, a: Byte) : this(RGBA.pack(r.toUnsigned(), g.toUnsigned(), b.toUnsigned(), a.toUnsigned()))
//
//    inner class VV {
//        operator fun get(n: Int) = (v ushr (8 * n)) and 0xFF
//        operator fun set(n: Int, value: Int) = run { v = (v and (0xFF shl (8 * n)).inv()) or (value and 0xFF) shl (8 * n) }
//    }
//
//    val vv = VV()
//
//    companion object {
//        fun avg(vararg v: Color): Color {
//            val c = Color()
//            val vv = IntArray(4)
//            for (n in 0 until v.size) for (m in 0 until 4) vv[m] += v[n].vv[m]
//            for (m in 0 until 4) c.vv[m] = vv[m] / v.size
//            return c
//        }
//    }
//
//    operator fun plus(a: Color): Color {
//        val c = Color(0)
//        for (n in 0 until 4) c.vv[n] = this.vv[n] + a.vv[n]
//        return c
//    }
//
//    override fun toString(): String = format("#%02X%02X%02X%02X", r, g, b, a)
//}
//static assert(Color.sizeof  == 4, "Invalid size for Color");

// Class to uncompress "CompressedBG" files.
class CompressedBG {
    // Header for the CompressedBG.
    class Header(
            val magic: ByteArray = ByteArray(0x10),
            val w: Short = 0,
            val h: Short = 0,
            val bpp: Int = 0,
            val _pad0: IntArray = IntArray(2),
            val data1_len: Int = 0,
            val data0_val: Int = 0,
            val data0_len: Int = 0,
            val hash0: Byte = 0,
            val hash1: Byte = 0,
            val _unknown: Byte = 0
    ) {
        companion object {
            fun read(s: SyncStream) = s.run {
                Header(
                        magic = readBytes(0x10),
                        w = readS16_le().toShort(),
                        h = readS16_le().toShort(),
                        bpp = readS32_le(),
                        _pad0 = readIntArray_le(2),
                        data1_len = readS32_le(),
                        data0_val = readS32_le(),
                        data0_len = readS32_le(),
                        hash0 = readS8().toByte(),
                        hash1 = readS8().toByte(),
                        _unknown = readS8().toByte()
                )
            }
        }
    }

    // Node for the Huffman decompression.
    class Node(val vv: IntArray = IntArray(6)) {
        constructor(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) : this(intArrayOf(a, b, c, d, e, f))

        override fun toString(): String = format("(%d, %d, %d, %d, %d, %d)", vv[0], vv[1], vv[2], vv[3], vv[4], vv[5])
    }

    //static assert(Header.sizeof == 0x30, "Invalid size for CompressedBG.Header");
    //static assert(Node.sizeof   == 24  , "Invalid size for CompressedBG.Node");

    var header = Header()
    var data0 = ByteArray(0)
    var table = IntArray(0x100)
    var table2 = Array(0x1FF) { Node() }
    var data1 = ByteArray(0)
    var data = IntArray(0)

    constructor(name: String) : this(File(name).readBytes().openSync())

    constructor(s: SyncStream) {
        header = Header.read(s)
        assert(header.magic.toString(ASCII) == "CompressedBG___\u0000")
        data0 = s.readBytes(header.data0_len)
        val datahf = s.readBytes((s.length - s.position).toInt())

        decode_chunk0(data0, header.data0_val)
        // Check the decoded chunk with a hash.
        assert(check_chunk0(data0, header.hash0.toUnsigned(), header.hash1.toUnsigned()))

        process_chunk0(data0, table, 0x100)
        var method2_res = method2(table, table2)
        data = IntArray(header.w * header.h)


        data1 = data1.copyOf(header.data1_len)
        uncompress_huffman(datahf, data1, table2, method2_res)
        val data3 = uncompress_rle(data1, ByteArray(header.w * header.h * 4))

        unpack_real(data, data3)
    }

    companion object {
        fun decode_chunk0(data: ByteArray, ihash_val: Int) {
            val hash_val = IntRef(ihash_val)
            //writefln("%08X", hash_val);

            for (n in 0 until data.size) {
                val _old = data.getu(n)
                val hash = hash_update(hash_val) and 0xFF
                data[n] = (data.getu(n) - hash).toByte()
                //val _ = data[n]
                //writefln("%02X-%02X -> %02X", _old, hash, _new);
            }
        }

        fun check_chunk0(data: ByteArray, hash_dl: Int, hash_bl: Int): Boolean {
            var dl = 0
            var bl = 0
            for (n in data.indices) {
                val c = data.getu(n)
                dl += c
                bl = bl xor c;
            }
            return (dl == hash_dl) && (bl == hash_bl)
        }

        fun process_chunk0(data0: ByteArray, table: IntArray, count: Int = 0x100) {
            val ptr = IntRef(0)
            for (n in 0 until count) table[n] = readVariable(data0, ptr)
        }

        fun method2(table1: IntArray, table2: Array<Node>): Int {
            var sum_of_values = 0

            run {
                // Verified.
                for (n in 0 until 0x100) {
                    table2[n] = Node((table1[n] > 0).toInt(), table1[n], 0, -1, n, n)
                    sum_of_values += table1[n]
                    //writefln(table2[n]);
                }
                //writefln(sum_of_values);
                if (sum_of_values == 0) return -1
            }

            run {
                // Verified.
                for (n in 0 until 0x100 - 1) table2[0x100 + n] = Node(0, 0, 1, -1, -1, -1)

                //std_file_write("table_out", cast(ubyte[])cast(void[])*(&table2[0 until table2.length]));
            }

            var cnodes = 0x100
            val vinfo = IntArray(2)

            while (true) {
                for (m in 0 until 2) {
                    vinfo[m] = -1

                    // Find the node with min_value.
                    var min_value = 0xFFFFFFFFL.toInt()
                    for (n in 0 until cnodes) {
                        val cnode = table2[n]

                        if (cnode.vv[0] != 0 && (cnode.vv[1] < min_value)) {
                            vinfo[m] = n
                            min_value = cnode.vv[1]
                        }
                    }

                    if (vinfo[m] != -1) {
                        with(table2[vinfo[m]]) {
                            vv[0] = 0
                            vv[3] = cnodes
                        }
                    }
                }

                //assert(0 == 1);

                val node = Node(
                        1,
                        (if (vinfo[1] != 0xFFFFFFFFL.toInt()) table2[vinfo[1]].vv[1] else 0) + table2[vinfo[0]].vv[1],
                        1,
                        -1,
                        vinfo[0],
                        vinfo[1]
                )

                //writefln("node(%03x): ", cnodes, node);
                table2[cnodes++] = node

                if (node.vv[1] == sum_of_values) break
            }

            return cnodes - 1
        }

        fun uncompress_huffman(src: ByteArray, dst: ByteArray, nodes: Array<Node>, method2_res: Int) {
            var mask = 0x80
            var s = 0
            var iter = 0

            for (n in 0 until dst.size) {
                var cvalue = method2_res

                if (nodes[method2_res].vv[2] == 1) {
                    do {
                        val bit = ((src.getu(s) and mask) != 0).toInt()
                        mask = mask ushr 1

                        cvalue = nodes[cvalue].vv[4 + bit]

                        if (mask == 0) {
                            s++
                            mask = 0x80
                        }
                    } while (nodes[cvalue].vv[2] == 1)
                }

                dst[n] = cvalue.toByte()
            }
        }

        fun uncompress_rle(src: ByteArray, dst: ByteArray): ByteArray {
            val s = IntRef(0)
            var d = 0
            var type = false

            while (s.v < src.size) {
                val len = readVariable(src, s)
                // RLE (for byte 00).
                if (type) {
                    for (n in 0 until len) dst[d++] = 0
                }
                // Copy from stream.
                else {
                    for (n in 0 until len) dst[d++] = src[s.v++]
                }
                type = !type
            }
            return dst.copyOf(d)
        }
    }

    fun unpack_real(output: IntArray, data0: ByteArray) {
        when (header.bpp) {
            24, 32 -> unpack_real_24_32(output, data0, header.bpp)
        //case 8: break; // Not implemented yet.
            else -> assert(false) { format("Unimplemented BPP %d", header.bpp) }
        }
    }

    fun unpack_real_24_32(dst: IntArray, src: ByteArray, bpp: Int = 32) {
        var c = if (bpp == 32) RGBA.pack(0, 0, 0, 0xFF) else RGBA.pack(0, 0, 0, 0)
        var s = 0
        var d = 0

        fun extract_32(): Int = RGBA.pack(src.getu(s + 0), src.getu(s + 1), src.getu(s + 2), src.getu(s + 3)).apply { s += 4 }
        fun extract_24(): Int = RGBA.pack(src.getu(s + 0), src.getu(s + 1), src.getu(s + 2), 0).apply { s += 3 }

        val extract = if (bpp == 32) ::extract_32 else ::extract_24
        fun extract_up(): Int = dst[d - header.w]

        for (x in 0 until header.w) {
            val vv = extract()
            c = RGBA.add(c, vv)
            dst[d++] = vv
        }
        for (y in 1 until header.h) {
            c = RGBA.add(extract_up(), extract())
            dst[d++] = c
            for (x in 1 until header.w) {
                c = RGBA.add(RGBA.avg(c, extract_up()), extract())
                dst[d++] = c
            }
        }
    }

    fun write_tga(name: String) {
        File(name).writeBytes(TGA.encode(Bitmap32(header.w.toInt(), header.h.toInt(), data))); }
}

class DSC {
    // Header for DSC files.
    class Header(
            val magic: ByteArray = ByteArray(0x10),
            val hash: Int = 0,
            val usize: Int = 0,
            val v2: Int = 0,
            val _pad: Int = 0
    ) {
        init {
            val m = magic.toString(UTF8)
            assert(m == "DSC FORMAT 1.00\u0000") { format("Not a DSC file '$m'") }
            assert(usize <= 0x3_000_000) { format("Too big uncompressed size '%d'", usize) }
        }

        companion object {
            fun read(s: SyncStream) = s.run {
                Header(
                        magic = readBytes(0x10),
                        hash = readS32_le(),
                        usize = readS32_le(),
                        v2 = readS32_le(),
                        _pad = readS32_le()
                )
            }
        }
    }

    // A node for the huffman tree.
    class Node {
        var has_childsInt: Int = 0
        var leaf_value: Int = 0
        val childs = IntArray(2)
        var has_childs: Boolean get() = (has_childsInt != 0); set(value) = run { has_childsInt = value.toInt() }
        var node_left: Int get() = childs[0]; set(v) = run { childs[0] = v }
        var node_right: Int get() = childs[1]; set(v) = run { childs[1] = v }
        override fun toString(): String = format("(childs:%08X, leaf:%08X, L:%08X, R:%08X)", has_childsInt, leaf_value, node_left, node_right)
    }

    // Check the sizes for the class structs.
    //static assert (Header.sizeof == 0x20, "Invalid size for DSC.Header");
    //static assert (Node.sizeof   == 4*4 , "Invalid size for DSC.Node");

    lateinit var header: Header
    var data = byteArrayOf()

    constructor(name: String) : this(File(name).readBytes().openSync())

    constructor(s: SyncStream) {
        header = Header.read(s)

        val src = s.readBytes(s.available.toInt())
        val nodes = Array(0x400) { Node() }
        // Decrypt and initialize the huffman tree.
        CompressionInit(header.hash, src, nodes)
        // Decompress the data using that tree.
        data = CompressionDo(src.copyOfRange(0x200, src.size), ByteArray(header.usize), nodes)
    }

    companion object {
        // Initializes the huffman tree.
        fun CompressionInit(ihash: Int, src: ByteArray, nodes: Array<Node>) {
            //println("ihash: $ihash")
            val hash = IntRef(ihash)
            // Input asserts.
            assert(src.size >= 0x200)

            // Output asserts.

            val buffer = IntArray(0x200)
            val vector0 = IntArray(0x400)
            var buffer_len = 0

            // Decrypt the huffman header.
            for (n in 0 until buffer.size) {
                val v = (src[n] - (hash_update(hash) and 0xFF)) and 0xFF
                //println("N: $n --> $v")
                //src[n] = v;
                if (v != 0) buffer[buffer_len++] = (v shl 16) + n
            }
            //writefln(src[0x000 until 0x100]); writefln(src[0x100 until 0x200]);

            // Sort the used slice of the buffer.
            val sorted = buffer.copyOf(buffer_len).sorted()
            for (n in sorted.indices) buffer[n] = sorted[n]

            var toggle = 0
            var cnt0_a = 0
            var nn = 0
            var value_set = 1
            var dec0 = 1
            vector0[0] = 0
            val v13a = vector0
            var v13 = 0

            var buffer_cur = 0
            //println("buffer_len: $buffer_len")
            while (buffer_cur < buffer_len - 1) {
                toggle = toggle xor 0x200
                var group_count = 0
                val vector0_ptr_init = toggle
                var vector0_ptr = vector0_ptr_init

                while (nn == HIWORD(buffer[buffer_cur])) {
                    nodes[v13a[v13]].has_childs = false
                    nodes[v13a[v13]].leaf_value = buffer[buffer_cur + 0] and 0x1FF
                    buffer_cur++
                    v13++
                    group_count++
                }

                val v18 = 2 * (dec0 - group_count)
                if (group_count < dec0) {
                    dec0 = (dec0 - group_count)
                    for (dd in 0 until dec0) {
                        //println("" + v13 + " : " + v13a[v13])
                        nodes[v13a[v13]].has_childs = true
                        for (m in 0 until 2) {
                            nodes[v13a[v13]].childs[m] = value_set
                            vector0[vector0_ptr++] = value_set
                            value_set++
                        }
                        v13++
                    }
                }
                dec0 = v18
                v13 = vector0_ptr_init
                nn++
            }

        }

        fun CompressionDo(src: ByteArray, dst: ByteArray, nodes: Array<Node>): ByteArray {
            //uint v2 = header.v2;

            var bits = 0
            var nbits = 0
            var s = 0
            var d = 0

            //writefln("--------------------");

            // Check the input and output pointers.
            while ((d < dst.size) && (s < src.size)) {
                var nentry = 0

                // Look over the tree.
                while (nodes[nentry].has_childs) {
                    // No bits left. Let's extract 8 bits more.
                    if (nbits == 0) {
                        nbits = 8
                        bits = src.getu(s++)
                    }
                    //writef("%b", (bits >> 7) & 1);
                    nentry = nodes[nentry].childs[(bits ushr 7) and 1]
                    nbits--
                    bits = (bits shl 1) and 0xFF
                }
                //writefln();

                // We are in a leaf.
                val info = LOWORD(nodes[nentry].leaf_value)

                // Compressed chunk.
                if (HIBYTE(info) == 1) {
                    var cvalue = bits ushr (8 - nbits)
                    var nbits2 = nbits
                    if (nbits < 12) {
                        var bytes = ((11 - nbits) ushr 3) + 1
                        nbits2 = nbits
                        while (bytes-- != 0) {
                            cvalue = src.getu((s++) + (cvalue shl 8))
                            nbits2 += 8
                        }
                    }
                    nbits = nbits2 - 12
                    bits = LOBYTE(cvalue shl (8 - (nbits2 - 12)))

                    val offset = (cvalue ushr (nbits2 - 12)) + 2
                    var ring_ptr = d - offset
                    var count = LOBYTE(info) + 2

                    //writefln("LZ(%d, %d)", -offset, count);

                    assert((ring_ptr >= 0) && (ring_ptr + count < dst.size)) { "Invalid reference pointer" }
                    //assert((dst_ptr + count > dst.ptr + dst.length), "Buffer overrun");

                    // Copy byte to byte to avoid overlapping issues.
                    while (count-- != 0) dst[d++] = dst[ring_ptr++]
                }
                // Uncompressed byte.
                else {
                    dst[d++] = LOBYTE(info).toByte()
                }
            }
            try {
                assert(s == src.size) { "Didn't read all the bytes from the input buffer" }
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            return dst.copyOf(d)
        }
    }

    // Allow storing the data in a stream.
    fun save(name: String) {
        File(name).writeBytes(data)
    }
}

class MatchResult(
    var pos: Int = 0,
    var len: Int = 0
)

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

fun varbits(v: Long, bits: Int): String {
    if (bits == 0) return ""
    return format(format("%%0%db", bits), v)
}

class BitWritter {
    private val data = ByteArrayBuilder()
    var cval: Long = 0L
    var av_bits: Int = 8

    companion object {
        fun mask(bits: Int): Long = (1L shl bits) - 1

        fun reverse(b: Int): Int {
            var b = b
            b = b and 0xF0 shr 4 or (b and 0x0F shl 4)
            b = b and 0xCC shr 2 or (b and 0x33 shl 2)
            b = b and 0xAA shr 1 or (b and 0x55 shl 1)
            return b
        }
    }

    //fun putbit (bit: Boolean) {
    //    if (bit) {
    //        cval = cval or (1 shl --av_bits)
    //    } else {
    //        --av_bits
    //    }
    //    if (av_bits == 0) flush()
    //}
    //
    //fun write (ins_val: Long, ins_bits: Int) {
    //    for (n in 0 until ins_bits) {
    //        val bit = ((ins_val ushr (ins_bits - n - 1)) and 1) != 0
    //        putbit(bit)
    //    }
    //}

    fun write(ins_val: Long, ins_bits: Int) {
        var ins_val: Long = ins_val
        var ins_bits: Int = ins_bits

        //writefln("%s", varbits(ins_val, ins_bits));
        val ins_bits0 = ins_bits

        while (ins_bits > 0) {
            val bits = min(ins_bits, av_bits)
            val extract = (ins_val ushr (ins_bits0 - bits)) and mask(bits)
            //writefln("  %s", varbits(extract, bits));

            cval = cval or (extract shl (av_bits - bits))

            ins_val = ins_val shl bits
            ins_bits -= bits
            av_bits -= bits
            if (av_bits <= 0) flush()
        }
    }

    fun flush() {
        if (av_bits == 8) return
        //writefln("  byte: %08b", cval);
        data.append(cval.toByte())
        av_bits = 8
        cval = 0
        //exit(0);
    }

    fun finish(): ByteArray {
        flush()
        return data.toByteArray()
    }
}

class MNode(
        var value: Int = 0,
        var freq: Int = 0,
        var level: Int = 0
) : Comparable<MNode> {
    //union
    //{
    //    struct { int value, freq; }
    //    long freq_value
    //}
    var encode: Int = 0
    var parent: MNode? = null
    var childs: Array<MNode?> = arrayOf(null, null)

    override fun compareTo(that: MNode): Int {
        //return this.freq_value - that.freq_value;
        val r = this.freq - that.freq
        if (r == 0) return this.value - that.value
        return r
    }

    override fun toString(): String = format("(%08X, %08X, %08X, %010b, [%d, %d])", value, freq, level, encode, childs[0] != null, childs[1] != null)
    val leaf: Boolean get() = (childs[0] == null) && (childs[1] == null)

    companion object {
        fun show(nodes: List<MNode>) {
            for (node in nodes) println(node)
        }

        fun findWithoutParent(nodes: List<MNode>, start: Int = 0): Int {
            for ((pos, node) in nodes[start until nodes.length].withIndex()) if (node.parent == null) return start + pos
            return -1
        }
    }

    fun propagateLevels(level: Int = 0, encode: Int = 0) {
        this.level = level
        this.encode = encode
        for ((k, node) in childs.withIndex()) node?.propagateLevels(level + 1, (encode shl 1) or k)
        //foreach (k, node; childs) if (node !is null) node.propagateLevels(level + 1, encode | (k << level));
    }
}

fun extract_levels(freqs: IntArray, levels: IntArray): List<MNode> {
    assert(freqs.size == levels.size)

    var cnodes = arrayListOf<MNode>()

    for ((value, freq) in freqs.withIndex()) if (freq > 0) cnodes.add(MNode(value, freq))
    while (true) {
        cnodes = ArrayList(cnodes.sorted())
        val node1 = MNode.findWithoutParent(cnodes, 0)
        if (node1 == -1) break // No nodes left without parent.
        val node2 = MNode.findWithoutParent(cnodes, node1 + 1)
        if (node2 == -1) break // No nodes left without parent.
        val node_l = cnodes[node1]
        val node_r = cnodes[node2]
        val node_p = MNode(-1, node_l.freq + node_r.freq, 1)
        node_p.childs[0] = node_r
        node_p.childs[1] = node_l
        node_r.parent = node_p
        node_l.parent = node_p
        cnodes.add(node_p)
    }
    cnodes[cnodes.length - 1].propagateLevels()
    //MNode.show(cnodes);

    for (n in 0 until levels.size) levels[n] = 0
    for (node in cnodes) if (node.leaf) levels[node.value] = node.level

    val lnodes = Array(freqs.size) { MNode() }
    for (node in cnodes) if (node.leaf) lnodes[node.value] = node

    assert(lnodes.size == freqs.size)

    return lnodes.toList()
}


data class RNode(var v: Long = 0L, var bits: Int = 0) {
    companion object {
        fun iterate(rnodes: Array<RNode>, nodes: Array<DSC.Node>, cnode: Int = 0, level: Int = 0, vv: Long = 0) {
            if (nodes[cnode].has_childs) {
                for ((k, ccnode) in nodes[cnode].childs.withIndex()) {
                    iterate(rnodes, nodes, ccnode, level + 1, (vv shl 1) or k.toLong())
                }
            } else {
                rnodes[nodes[cnode].leaf_value and 0x1FF].apply {
                    this.v = vv
                    this.bits = level
                }
            }
        }
    }

    override fun toString(): String {
        return if (bits != 0) format(format("%%0%db", bits), v) else ""
    }
}

fun decompress(data: ByteArray): ByteArray {
    return DSC(data.openSync()).data
}

fun compress(data: ByteArray, level: Int = 0, seed: Int = 0): ByteArray {
    val data = UByteArray(data)
    val min_lz_len = 2
    val max_lz_len = 0x100 + 2
    val max_lz_pos = 0x1000
    val min_lz_pos = 2
    var max_lz_len2 = max_lz_len
    var max_lz_pos2 = max_lz_len

    data class Encode(var bits: Int, var value: Int)

    val encode = Array(0x200) { Encode(0, 0) }

    val freq = IntArray(0x200)
    val levels = IntArray(0x200)

    data class Block(var value: Int, var pos: Int)

    val blocks = arrayListOf<Block>()

    max_lz_len2 = (max_lz_len * level) / 9
    max_lz_pos2 = (max_lz_pos * level) / 9

    val mresult = MatchResult()
    var n = 0
    while (n < data.size) {
        mresult.pos = 0
        mresult.len = 0
        val max_len = min(max_lz_len2, data.size - n)
        if (level > 0) {
            find_variable_match(
                data[max(0, n - max_lz_pos2) until n + max_len],
                data[n until n + max_len],
                mresult,
                min_lz_pos
            )
        }

        // Compress.
        var id = 0
        if (mresult.len >= min_lz_len) {
            val encoded_len = mresult.len - min_lz_len
            id = 0x100 or (encoded_len and 0xFF)
            blocks += Block(id, mresult.pos)
            n += mresult.len
        } else {
            id = 0x000 or (data[n] and 0xFF)
            blocks += Block(id, 0)
            n++
        }
        freq[id]++
    }

    val rnodes = Array(0x200) { RNode() }
    val cnodes = Array(0x400) { DSC.Node() }
    extract_levels(freq, levels)
    //val nodes = extract_levels(freq, levels);
    val r = ByteArrayBuilder()

    val hash_val = IntRef(0x000505D3 + seed)
    val init_hash_val = hash_val.v

    fun ins_int(v: Int) {
        r += ByteArray(4).apply { write32_le(0, v) }
    }

    r += "DSC FORMAT 1.00\u0000".toByteArray(UTF8)
    ins_int(hash_val.v)
    ins_int(data.length)
    ins_int(blocks.length)
    ins_int(0)

    val seedData = ByteArray(0x200) { (levels[it] + (hash_update(hash_val) and 0xFF)).toByte() }
    r += seedData
    //println(seedData.toList())
    DSC.CompressionInit(init_hash_val, seedData, cnodes)
    RNode.iterate(rnodes, cnodes)

    //writefln("rnodes:"); foreach (k, rnode; rnodes) if (rnode.bits > 0) writefln("  %03X:%s", k, rnode);

    // Write bits.
    val bitw = BitWritter()
    for (block in blocks) {
        val rnode = rnodes[block.value]
        if ((block.value and 0x100) != 0) {
            //writefln("BLOCK:LZ(%d, %d)", -(block.pos - 2), (block.value & 0xFF) + 2);
        } else {
            //writefln("BLOCK:BYTE(%02X)", block.value & 0xFF);
        }
        bitw.write(rnode.v, rnode.bits)
        if ((block.value and 0x100) != 0) {
            bitw.write(block.pos.toLong(), 12)
            //bitw.finish();
        }
    }
    r += bitw.finish()

    return r.toByteArray()
    //writefln(nodes[0]);
    //writefln(levels);
}

class ImageHeader(
        var width: Short = 0,
        var height: Short = 0,
        var bpp: Int = 0,
        var zpad0: Int = 0,
        var zpad1: Int = 0
) {

    companion object {
        fun read(s: SyncStream): ImageHeader = s.run {
            ImageHeader(
                    width = readS16_le().toShort(),
                    height = readS16_le().toShort(),
                    bpp = readS32_le(),
                    zpad0 = readS32_le(),
                    zpad1 = readS32_le()
            )
        }
    }
}

//suspend fun main_s(args: Array<String>) {
fun main_s(args: Array<String>) {
    // Shows the help for the usage of the program.
    fun show_help() {
        writefln("Ethornell utility %s - soywiz - 2009 - Build %s", _version, __TIMESTAMP__)
        writefln("Knows to work with English Shuffle! and Edelweiss with Ethornell 1.69.140")
        writefln()
        writefln("ethornell <command> <parameters>")
        writefln()
        writefln("  -l       List the contents of an arc pack")
        writefln("  -x[0-9]  Extracts the contents of an arc pack (uncompressing when l>0)")
        writefln("  -p[0-9]  Packs and compress a folder")
        writefln()
        writefln("  -d       Decompress a single file")
        writefln("  -c[0-9]  Compress a single file")
        writefln("  -t[0-9]  Test the compression")
        writefln()
        writefln("  -h       Show this help")
    }

    // Throws an exception if there are less parameters than the required.
    fun expect_params(count: Int) {
        if (args.size < (count + 2)) throw(ShowHelpException(format("Expected '%d' params and '%d' received", count, args.size - 2)))
    }

    try {
        if (args.size < 2) throw ShowHelpException()

        var params = arrayListOf<String>()
        if (args.size > 2) params = ArrayList(args.copyOfRange(2, args.size).toList())


        fun check_image(i: ImageHeader): Boolean =
                ((i.bpp % 8) == 0) && (i.bpp > 0) && (i.bpp <= 32) &&
                        (i.width > 0) && (i.height > 0) &&
                        (i.width < 8096) && (i.height < 8096) &&
                        (i.zpad0 == 0) && (i.zpad1 == 0)

        fun write_image(ih: ImageHeader, out_file: String, data: ByteArray) {
            //val f = BufferedFile(out_file, FileMode.OutNew);
            val bmp = when (ih.bpp) {
                32 -> RGBA.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data)
                24 -> RGB.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data)
                else -> throw(Exception("Unknown bpp"))
            }

            std_file_write(out_file, TGA.encode(bmp))
            //f.close();
        }

        when (args[1][0 until 2]) {
        // List.
            "-l" -> {
                expect_params(1)
                val arc_name = params[0]

                // Check if the arc actually exists.
                assert(std_file_exists(arc_name)) { format("File '%s' doesn't exists", arc_name) }

                // Writes a header with the arc file that we are processing.
                writefln("----------------------------------------------------------------")
                writefln("ARC: %s", arc_name)
                writefln("----------------------------------------------------------------")
                // Iterate over the ARC file and write the files.
                for (e in ARC(arc_name)) println(e.name)
            }
        // Extact + uncompress.
            "-x" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val arc_name = params[0]

                // Check if the arc actually exists.
                assert(File(arc_name).exists()) { format("File '%s' doesn't exists", arc_name) }

                // Determine the output path and create the folder if it doesn't exists already.
                val out_path = arc_name + ".d"
                ignoreErrors { File(out_path).mkdirs() }

                // Iterate over the arc file.
                for (e in ARC(arc_name)) {
                    if (params.length >= 2) {
                        var found = false
                        for (filter in params[1 until params.length]) {
                            if (filter == e.name) {
                                found = true; break; }
                        }
                        if (!found) continue
                    }
                    val s = e.open()
                    writef("%s...", e.name)
                    var out_file: String = ""
                    if (params.length >= 2) {
                        out_file = e.name
                    } else {
                        out_file = "$out_path/${e.name}"
                    }

                    try {
                        var result = "Ok"
                        // Check the first 0x10 bytes to determine the magic of the file.
                        when (s.sliceWithStart(0L).readString(0x10, ASCII)) {
                        // Encrypted+Static Huffman+LZ
                            "DSC FORMAT 1.00\u0000" -> {
                                writef("DSC...")
                                if (File(out_file).exists()) {
                                    result = "Exists"
                                } else {
                                    var data = byteArrayOf()
                                    if (level == 0) {
                                        data = s.readBytes(s.length.toInt())
                                    } else {
                                        val dsc = DSC(s)
                                        data = dsc.data
                                    }
                                    val ih: ImageHeader = ImageHeader.read(data.openSync())
                                    if (check_image(ih)) {
                                        writef("Image...BPP(%d)...", ih.bpp)
                                        out_file += ".tga"
                                        if (std_file_exists(out_file)) throw(Exception("Exists"))
                                        write_image(ih, out_file, data.copyOfRange(0x10, data.size))
                                    } else {
                                        std_file_write(out_file, data)
                                    }
                                }
                            }
                        // Encrypted+Dynamic Huffman+RLE+LZ+Unpacking+Row processing
                            "CompressedBG___\u0000" -> {
                                out_file += ".tga"
                                writef("CBG...")
                                if (std_file_exists(out_file)) throw(Exception("Exists"))
                                val cbg = CompressedBG(s)
                                cbg.write_tga(out_file)
                            }
                        // Uncompressed/Unknown.
                            else -> {
                                val ss = s.sliceWithStart(6L)
                                val width: Int
                                val height: Int
                                val bpp: Int
                                val ih = ImageHeader.read(ss)
                                if (check_image(ih)) {
                                    writef("Image...BPP(%d)...", ih.bpp)
                                    out_file += ".tga"
                                    if (std_file_exists(out_file)) throw(Exception("Exists"))
                                    s.position = 0x10
                                    write_image(ih, out_file, s.readBytes((s.length - s.position).toInt()))
                                } else {
                                    writef("Uncompressed...")
                                    if (std_file_exists(out_file)) throw(Exception("Exists"))
                                    File(out_file).writeBytes(s.readAll())
                                }
                            }
                        }
                        println(result)
                    }
                    // There was an error, write it.
                    catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        // Packs and compress a file.
            "-p" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'

                expect_params(1)
                val folder_in = params[0]
                val arc_out = folder_in[0 until folder_in.length - 2]

                // Check if the file actually exists.
                assert(std_file_exists(folder_in)) { format("Folder '%s' doesn't exists", folder_in) }
                assert(folder_in[folder_in.length - 6 until folder_in.length] == ".arc.d") { format("Folder '%s', should finish by .arc.d", folder_in) }
                val count = listdir(folder_in).size
                val s = MemorySyncStream()
                s.writeString("PackFile    ")
                s.write32_le(count.toInt())
                var pos = 0

                for ((k, file_name) in listdir(folder_in).withIndex()) {
                    writef("%s...", file_name)
                    val data: ByteArray = File(folder_in + "/" + file_name).readBytes()
                    val cdata: ByteArray
                    // Already compressed.
                    if (data.sliceArray(0 until 0x10).toString(ASCII) == "DSC FORMAT 1.00\u0000") {
                        cdata = data
                        writefln("Already compressed")
                    }
                    // Not compressed.
                    else {
                        cdata = compress(data, level)
                        writefln("Compressed")
                    }
                    s.position = (0x10 + count * 0x20 + pos).toLong()
                    s.writeBytes(cdata)
                    s.position = (0x10 + k * 0x20).toLong()
                    s.writeString(file_name)
                    while ((s.position % 0x10L) != 0L) s.write8(0)
                    s.write32_le(pos)
                    s.write32_le(cdata.size)
                    s.write32_le(0)
                    s.write32_le(0)
                    pos += cdata.size
                }
                s.close()
                File(arc_out).writeBytes(s.toByteArray())
            }
        // Decompress a single file.
            "-d" -> {
                expect_params(1)
                val file_name = params[0]
                val out_file = file_name + ".u"

                // Check if the file actually exists.
                assert(std_file_exists(file_name)) { format("File '%s' doesn't exists", file_name) }

                run {
                    val s = File(file_name).readBytes().openSync()
                    when (s.slice().readString(0x10, ASCII)) {
                    // Encrypted+Dynamic Huffman+RLE+LZ+Unpacking+Row processing
                        "CompressedBG___\u0000" -> {
                            val cbg = CompressedBG(s)
                            cbg.write_tga(out_file)
                        }
                        else -> {
                            val dsc = DSC(file_name)
                            dsc.save(out_file)
                        }
                    }
                }
            }
        // Compress a single file.
            "-c" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val file_name = params[0]
                val out_file = file_name + ".c"

                // Check if the file actually exists.
                assert(File(file_name).exists()) { format("File '%s' doesn't exists", file_name) }

                File(out_file).writeBytes(compress(std_file_read(file_name), level))
            }
        // Test the compression.
            "-t" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val file_name = params[0]

                // Check if the file actually exists.
                assert(File(file_name).exists()) { format("File '%s' doesn't exists", file_name) }

                val uncompressed0 = std_file_read(file_name)
                val compressed = compress(uncompressed0, level)
                val dsc = DSC(compressed.openSync())
                val uncompressed1 = dsc.data

                assert(uncompressed0.contentEquals(uncompressed1)) { "Failed" }
                writefln("Ok")
            }
        // Help command.
            "-h" -> throw ShowHelpException()
        // Unknown command.
            else -> {
                throw ShowHelpException(format("Unknown command '%s'", args[1]))
            }
        }

        exitProcess(0)
    }
    // Catch a exception to show the help/usage.
    catch (e: ShowHelpException) {
        show_help()
        e.printStackTrace()
        exitProcess(0)
    }
    // Catch a generic unhandled exception.
    catch (e: Throwable) {
        e.printStackTrace()
        exitProcess(-1)
    }
}
