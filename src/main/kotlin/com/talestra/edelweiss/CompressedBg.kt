package com.talestra.edelweiss

import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.color.BGRA
import com.soywiz.korim.color.RGBA
import com.soywiz.korio.error.invalidArg
import com.soywiz.korio.lang.ASCII
import com.soywiz.korio.lang.toString
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.getu
import com.soywiz.korio.util.toInt
import com.soywiz.korio.util.toUnsigned
import com.soywiz.korio.util.ult

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
        init {
            if (magic.toString(ASCII) != MAGIC) invalidArg("Not a CompressedBG")
        }

        companion object {
            val MAGIC = "CompressedBG___\u0000"

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

    fun detect(data: ByteArray): Boolean {
        return try {
            Header.read(data.openSync())
            true
        } catch (e: Throwable) {
            false
        }
    }

    // Node for the Huffman decompression.
    class Node(val vv: IntArray = IntArray(6)) {
        constructor(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int) : this(intArrayOf(a, b, c, d, e, f))

        fun child(index: Int) = vv[4 + index]
        fun child(index: Boolean) = vv[4 + index.toInt()]
        val isBranch: Boolean get() = vv[2] == 1
        val isLeaf: Boolean get() = vv[2] != 1

        override fun toString(): String = format("(%d, %d, %d, %d, %d, %d)", vv[0].toUnsigned(), vv[1].toUnsigned(), vv[2].toUnsigned(), vv[3].toUnsigned(), vv[4].toUnsigned(), vv[5].toUnsigned())
    }

    //static assert(Header.sizeof == 0x30, "Invalid size for CompressedBG.Header");
    //static assert(Node.sizeof   == 24  , "Invalid size for CompressedBG.Node");

    fun load(s: ByteArray): Bitmap32 = load(s.openSync())

    fun load(s: SyncStream): Bitmap32 {
        val header = Header.read(s)
        val data0 = decodeChunk0Inplace(s.readBytes(header.data0_len), header.data0_val)
        val datahf = s.readBytes(s.available.toInt())
        if (!checkChunk0(data0, header.hash0.toUnsigned(), header.hash1.toUnsigned())) invalidArg("Out of bounds")
        val table = readVariables(data0, 0x100)
        val table2 = Array(0x1FF) { Node() }
        val method2Res = method2(table, table2)
        val data = IntArray(header.w * header.h)


        //data1 = data1.copyOf(header.data1_len)
        val data1 = ByteArray(header.data1_len)
        uncompressHuffman(datahf, data1, table2, method2Res)
        val data3 = uncompressZeroRle(data1, ByteArray(header.w * header.h * 4))

        //for (d in data3) println(d)

        //File("uncompressed_after_rle_kt").writeBytes(data3)

        unpackApplyFilter(header, data, data3)
        return Bitmap32(header.w, header.h, data)
    }

    class MiniReader(val data: ByteArray, var pos: Int = 0) {
        val hasMore: Boolean get() = pos < data.size

        // Read a variable value from a pointer.
        fun readVariable(): Int {
            var c = 0
            var v = 0
            var shift = 0
            do {
                c = data.getu(pos++)
                v = v or ((c and 0x7F) shl shift)
                shift += 7
            } while ((c and 0x80) != 0)
            return v
        }

        fun readByte() = data[pos++]
    }

    private fun decodeChunk0Inplace(data: ByteArray, seed: Int): ByteArray {
        val hash = HashUpdater(seed)
        for (n in 0 until data.size) data[n] = (data.getu(n) - (hash.update() and 0xFF)).toByte()
        return data
    }

    private fun checkChunk0(data: ByteArray, hash_dl: Int, hash_bl: Int): Boolean {
        var dl = 0
        var bl = 0
        for (n in data.indices) {
            val c = data.getu(n)
            dl = (dl + c) and 0xFF
            bl = (bl xor c) and 0xFF
        }
        return (dl == hash_dl) && (bl == hash_bl)
    }

    private fun readVariables(data0: ByteArray, count: Int = 0x100): IntArray {
        val mr = MiniReader(data0)
        return IntArray(count) { mr.readVariable() }
    }

    private fun method2(table1: IntArray, table2: Array<Node>): Int {
        var sumOfValues = 0

        for (n in 0 until 0x100) {
            table2[n] = Node((table1[n] > 0).toInt(), table1[n], 0, -1, n, n)
            sumOfValues += table1[n]
        }
        if (sumOfValues == 0) return -1
        for (n in 0 until 0x100 - 1) table2[0x100 + n] = Node(0, 0, 1, -1, -1, -1)

        //for (t in table2) println(t)

        var cnodes = 0x100
        val children = IntArray(2)

        while (true) {
            for (m in 0 until 2) {
                children[m] = -1

                // Find the node with minValue.
                var minValue = 0.inv()
                for (n in 0 until cnodes) {
                    val cnode = table2[n]

                    if (cnode.vv[0] != 0 && (cnode.vv[1] ult minValue)) {
                        children[m] = n
                        minValue = cnode.vv[1]
                    }
                }

                if (children[m] != -1) {
                    with(table2[children[m]]) {
                        vv[0] = 0
                        vv[3] = cnodes
                    }
                }
            }

            //assert(0 == 1);

            val node = Node(
                    1,
                    (if (children[1] != -1) table2[children[1]].vv[1] else 0) + table2[children[0]].vv[1],
                    1,
                    -1,
                    children[0],
                    children[1]
            )

            //writefln("node(%03x): ", cnodes, node);
            table2[cnodes++] = node

            if (node.vv[1] == sumOfValues) break
        }

        return cnodes - 1
    }

    private fun uncompressHuffman(src: ByteArray, dst: ByteArray, nodes: Array<Node>, defaultValue: Int) {
        var mask = 0
        var bits = 0
        var srcPos = 0
        val LEAF_BIT = -2147483648 // 0x80000000

        // Pack node information in a int array for fastest performance
        val inodes = IntArray(nodes.size) {
            val node = nodes[it]
            if (node.isLeaf) {
                LEAF_BIT or it
            } else {
                (node.child(0) shl 0) or (node.child(1) shl 16)
            }
        }

        for (n in 0 until dst.size) {
            var node = inodes[defaultValue]

            while ((node and LEAF_BIT) == 0) {
                if (mask == 0) {
                    bits = src.getu(srcPos++)
                    mask = 0x80
                }

                val bit = ((bits and mask) != 0).toInt()
                mask = mask ushr 1
                node = inodes[(node ushr (16 * bit)) and 0x7FFF]
            }

            dst[n] = node.toByte()
        }

        //for (n in 0 until dst.size) {
        //    var cvalue = defaultValue
//
        //    while (nodes[cvalue].isBranch) {
        //        if (mask == 0) {
        //            bits = src.getu(srcPos++)
        //            mask = 0x80
        //        }
//
        //        val bit = (bits and mask) != 0
        //        mask = mask ushr 1
        //        cvalue = nodes[cvalue].child(bit)
        //    }
//
        //    dst[n] = cvalue.toByte()
        //}
    }

    private fun uncompressZeroRle(src: ByteArray, dst: ByteArray): ByteArray {
        val mr = MiniReader(src)
        var d = 0
        var type = false

        while (mr.hasMore) {
            val len = mr.readVariable()
            if (type) {
                // RLE (for byte 00).
                for (n in 0 until len) dst[d++] = 0
            } else {
                // Copy from stream.
                for (n in 0 until len) dst[d++] = mr.readByte()
            }
            type = !type
        }
        return dst.copyOf(d)
    }

    private fun unpackApplyFilter(header: Header, dst: IntArray, src: ByteArray) {
        val bpp = header.bpp
        when (bpp) {
            24, 32 -> {
                var c = if (bpp == 32) BGRA.pack(0, 0, 0, 0) else RGBA.pack(0, 0, 0, 0xFF)
                var s = 0
                var d = 0

                fun extract32(): Int = BGRA.pack(src.getu(s++), src.getu(s++), src.getu(s++), src.getu(s++))
                fun extract24(): Int = BGRA.pack(src.getu(s++), src.getu(s++), src.getu(s++), 0)

                val extract = if (bpp == 32) ::extract32 else ::extract24
                fun extractUp(): Int = dst[d - header.w]

                for (x in 0 until header.w) {
                    val vv = extract()
                    //println("%08X, %08X".format(vv, c))
                    c = RGBA.add(c, vv)
                    dst[d++] = c
                }

                for (y in 1 until header.h) {
                    c = RGBA.add(extractUp(), extract())
                    dst[d++] = c
                    for (x in 1 until header.w) {
                        val up = extractUp()
                        val cc = extract()
                        //println("%08X, %08X".format(up, cc))
                        c = RGBA.add(RGBA.avg(c, up), cc)
                        dst[d++] = c
                    }
                }
            }
            else -> TODO("Unimplemented BPP $bpp")
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
