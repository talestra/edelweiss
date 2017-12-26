package com.talestra.edelweiss

import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.color.BGRA
import com.soywiz.korim.color.RGBA
import com.soywiz.korio.lang.ASCII
import com.soywiz.korio.lang.toString
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.getu
import com.soywiz.korio.util.toInt
import com.soywiz.korio.util.toUnsigned

// Class to uncompress "CompressedBG" files.
object CompressedBg {
    // Header for the CompressedBG.
    class Header(
            val magic: ByteArray = ByteArray(0x10),
            val w: Int = 0,
            val h: Int = 0,
            val bpp: Int = 0,
            val _pad0: IntArray = IntArray(2),
            val data1_len: Int = 0,
            val data0_val: Int = 0,
            val data0_len: Int = 0,
            val hash0: Byte = 0,
            val hash1: Byte = 0,
            val _unknown: Short = 0
    ) {
        companion object {
            fun read(s: SyncStream) = s.run {
                Header(
                        magic = readBytes(0x10),
                        w = readU16_le(),
                        h = readU16_le(),
                        bpp = readS32_le(),
                        _pad0 = readIntArray_le(2),
                        data1_len = readS32_le(),
                        data0_val = readS32_le(),
                        data0_len = readS32_le(),
                        hash0 = readS8().toByte(),
                        hash1 = readS8().toByte(),
                        _unknown = readS16_le().toShort()
                )
            }
        }
    }

    // Node for the Huffman decompression.
    class Node(val vv: IntArray = IntArray(6)) {
        constructor(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) : this(intArrayOf(a, b, c, d, e, f))

        override fun toString(): String = format("(%d, %d, %d, %d, %d, %d)", vv[0].toUnsigned(), vv[1].toUnsigned(), vv[2].toUnsigned(), vv[3].toUnsigned(), vv[4].toUnsigned(), vv[5].toUnsigned())
    }

    //static assert(Header.sizeof == 0x30, "Invalid size for CompressedBG.Header");
    //static assert(Node.sizeof   == 24  , "Invalid size for CompressedBG.Node");

    fun load(s: ByteArray): Bitmap32 = load(s.openSync())

    fun load(s: SyncStream): Bitmap32 {
        val table = IntArray(0x100)
        val table2 = Array(0x1FF) { Node() }
        //var data1 = ByteArray(0)

        val header = Header.read(s)
        assert(header.magic.toString(ASCII) == "CompressedBG___\u0000") { "Not a compressedBG" }
        val data0 = s.readBytes(header.data0_len)
        val datahf = s.readBytes(s.available.toInt())

        //println(header.data0_val)
        //println(data0.toList())

        decode_chunk0(data0, header.data0_val)

        // Check the decoded chunk with a hash.

        //println(data0.size)
        //println(data0.toList())
        //println(header.hash0.toUnsigned())
        //println(header.hash1.toUnsigned())

        assert(check_chunk0(data0, header.hash0.toUnsigned(), header.hash1.toUnsigned())) { "Out of bounds" }

        process_chunk0(data0, table, 0x100)
        val method2_res = method2(table, table2)
        val data = IntArray(header.w * header.h)


        //data1 = data1.copyOf(header.data1_len)
        val data1 = ByteArray(header.data1_len)
        uncompress_huffman(datahf, data1, table2, method2_res)
        val data3 = uncompress_rle(data1, ByteArray(header.w * header.h * 4))

        //for (d in data3) println(d)

        //File("uncompressed_after_rle_kt").writeBytes(data3)

        unpack_real(header, data, data3)
        return Bitmap32(header.w, header.h, data)
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

    private fun decode_chunk0(data: ByteArray, ihash_val: Int) {
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

    private fun check_chunk0(data: ByteArray, hash_dl: Int, hash_bl: Int): Boolean {
        var dl = 0
        var bl = 0
        for (n in data.indices) {
            val c = data.getu(n)
            dl = (dl + c) and 0xFF
            bl = (bl xor c) and 0xFF
        }
        return (dl == hash_dl) && (bl == hash_bl)
    }

    private fun process_chunk0(data0: ByteArray, table: IntArray, count: Int = 0x100) {
        val ptr = IntRef(0)
        //println(data0)
        for (n in 0 until count) table[n] = readVariable(data0, ptr)
    }

    private fun method2(table1: IntArray, table2: Array<Node>): Int {
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

        //for (t in table2) println(t)

        var cnodes = 0x100
        val vinfo = IntArray(2)

        while (true) {
            for (m in 0 until 2) {
                vinfo[m] = -1

                // Find the node with min_value.
                var min_value = 0xFFFFFFFFL.toInt()
                for (n in 0 until cnodes) {
                    val cnode = table2[n]

                    if (cnode.vv[0] != 0 && (cnode.vv[1].toUnsigned() < min_value.toUnsigned())) {
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
                    (if (vinfo[1] != -1) table2[vinfo[1]].vv[1] else 0) + table2[vinfo[0]].vv[1],
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

    private fun uncompress_huffman(src: ByteArray, dst: ByteArray, nodes: Array<Node>, method2_res: Int) {
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

    private fun uncompress_rle(src: ByteArray, dst: ByteArray): ByteArray {
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

    private fun unpack_real(header: Header, output: IntArray, data0: ByteArray) {
        when (header.bpp) {
            24, 32 -> unpack_real_24_32(header, output, data0, header.bpp)
        //case 8: break; // Not implemented yet.
            else -> assert(false) { format("Unimplemented BPP %d", header.bpp) }
        }
    }

    private fun unpack_real_24_32(header: Header, dst: IntArray, src: ByteArray, bpp: Int = 32) {
        var c = if (bpp == 32) BGRA.pack(0, 0, 0, 0) else RGBA.pack(0, 0, 0, 0xFF)
        var s = 0
        var d = 0

        fun extract_32(): Int = BGRA.pack(src.getu(s++), src.getu(s++), src.getu(s++), src.getu(s++))
        fun extract_24(): Int = BGRA.pack(src.getu(s++), src.getu(s++), src.getu(s++), 0)

        val extract = if (bpp == 32) ::extract_32 else ::extract_24
        fun extract_up(): Int = dst[d - header.w]

        for (x in 0 until header.w) {
            val vv = extract()
            //println("%08X, %08X".format(vv, c))
            c = RGBA.add(c, vv)
            dst[d++] = c
        }

        for (y in 1 until header.h) {
            c = RGBA.add(extract_up(), extract())
            dst[d++] = c
            for (x in 1 until header.w) {
                val up = extract_up()
                val cc = extract()
                //println("%08X, %08X".format(up, cc))
                c = RGBA.add(RGBA.avg(c, up), cc)
                dst[d++] = c
            }
        }
    }


    // A color RGBA struct that defines methods to sum colors per component and to obtain average colors.
// @TODO: Move to Korim
    private fun RGBA.add(l: Int, r: Int): Int = RGBA.pack(
            (RGBA.getR(l) + RGBA.getR(r)) and 0xFF,
            (RGBA.getG(l) + RGBA.getG(r)) and 0xFF,
            (RGBA.getB(l) + RGBA.getB(r)) and 0xFF,
            (RGBA.getA(l) + RGBA.getA(r)) and 0xFF
    )

    private fun RGBA.avg(a: Int, b: Int): Int = RGBA.pack(
            ((RGBA.getR(a) + RGBA.getR(b)) / 2) and 0xFF,
            ((RGBA.getG(a) + RGBA.getG(b)) / 2) and 0xFF,
            ((RGBA.getB(a) + RGBA.getB(b)) / 2) and 0xFF,
            ((RGBA.getA(a) + RGBA.getA(b)) / 2) and 0xFF
    )
}
