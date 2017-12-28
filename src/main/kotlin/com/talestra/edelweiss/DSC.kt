package com.talestra.edelweiss

import com.soywiz.kmem.write32_le
import com.soywiz.korio.error.invalidOp
import com.soywiz.korio.lang.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.*
import com.soywiz.korma.ds.IntArrayList

object DSC {
    fun isCompressed(data: ByteArray): Boolean {
        return data.readString(0, 0x10, ASCII) == Header.MAGIC
    }

    fun decompressIfRequired(data: ByteArray): ByteArray {
        return if (isCompressed(data)) decompress(data) else data
    }

    fun decompress(data: ByteArray): ByteArray = decompress(data.openSync())

    fun decompress(s: SyncStream): ByteArray {
        val header = Header.read(s)
        val src = s.readBytes(s.available.toInt())
        val nodes = Array(0x400) { Node() }
        // Decrypt and initialize the huffman tree.
        CompressionInit(header.hash, src, nodes)
        // Decompress the data using that tree.
        return CompressionDo(src.copyOfRange(0x200, src.size), ByteArray(header.usize), nodes)
    }

    fun compressAndCheck(data: ByteArray, level: Int = 0, seed: Int = 0, check: Boolean = true): ByteArray {
        val compressed = compress(data, level, seed)
        if (check && !data.contentEquals(decompress(compressed))) invalidOp("Invalid compression!")
        return compressed
    }

    fun compress(data: ByteArray, level: Int = 0, seed: Int = 0): ByteArray {
        val data = UByteArray(data)
        val min_lz_len = 2
        val max_lz_len = 0xFF + min_lz_len
        val max_lz_pos = 0xFFF
        val min_lz_pos = 2

        val freq = IntArray(0x200)
        val levels = IntArray(0x200)

        val blocksValues = IntArrayList()
        val blocksPos = IntArrayList()

        //val max_lz_len2 = (max_lz_len * level) / 9
        //val max_lz_pos2 = (max_lz_pos * level) / 9

        val lz = LZ(nbits = 12)
        val lzSearch = level > 0
        val lzResult = lz.result
        //val mresult = LZ.MatchResult()
        var n = 0
        while (n < data.size) {
            if (lzSearch) {
                lz.findLargest(data.array, n, max_lz_len, min_lz_pos, maxChecks = 30)
                //val max_len = min(data.size - n, max_lz_len)
                //val res = LZ2.find_variable_match(
                //        data[max(0, n - max_lz_pos2) until n + max_len],
                //        data[n until n + max_len],
                //        min_lz_pos
                //)
                //println("" + lz.dataPos + ",$n : " + lzResult + " : " + res + " : ${data.array.slice(0 until n).toByteArray().hex} : ${data.array.slice(n until n + max_len).toByteArray().hex}")
                //println((n - lzResult.pos) - 2)
                //lzResult.pos = res.pos
                //lzResult.len = res.len
                lzResult.pos = (n - lzResult.pos) - 2
            } else {
                lzResult.pos = 0
                lzResult.len = 0
            }

            // Compress.
            var id = 0
            if (lzResult.len >= min_lz_len) {
                if (lzResult.len !in min_lz_len .. max_lz_len) invalidOp("Invalid LZ length $lzResult")
                if (lzResult.pos !in min_lz_pos .. max_lz_pos) invalidOp("Invalid LZ position $lzResult")
                //if (lzResult.pos >= n) invalidOp("Invalid LZ position II $lzResult")

                val encoded_len = lzResult.len - min_lz_len
                id = 0x100 or (encoded_len and 0xFF)
                blocksValues += id
                blocksPos += lzResult.pos
                if (lzSearch) lz.put(data.array, n, lzResult.len)
                n += lzResult.len
            } else {
                id = 0x000 or (data[n] and 0xFF)
                blocksValues += id
                blocksPos += 0
                if (lzSearch) lz.put(data[n].toByte())
                n++
            }
            freq[id]++
        }

        // Prevent just one repeated value problems with current implementation!
        if (freq.count { it != 0 } == 1) if (freq[0] != 0) freq[1]++ else freq[0]++

        val rnodes = Array(0x200) { RNode() }
        val cnodes = Array(0x400) { DSC.Node() }

        extractLevels(freq, levels)

        val r = ByteArrayBuilder()
        val init_hash_val = 0x000505D3 + seed
        val hash_val = HashUpdater(init_hash_val)

        fun ins_int(v: Int) = run { r += ByteArray(4).apply { write32_le(0, v) } }

        r += "DSC FORMAT 1.00\u0000".toByteArray(UTF8)
        ins_int(init_hash_val)
        ins_int(data.length)
        ins_int(blocksValues.length)
        ins_int(0)

        val seedData = ByteArray(0x200) { (levels[it] + (hash_val.update() and 0xFF)).toByte() }
        r += seedData
        //println(seedData.toList())
        DSC.CompressionInit(init_hash_val, seedData, cnodes)
        RNode.iterate(rnodes, cnodes)

        //writefln("rnodes:"); foreach (k, rnode; rnodes) if (rnode.bits > 0) writefln("  %03X:%s", k, rnode);

        // Write bits.
        //println("blocks:${blocks.size}")
        val bitw = BitWritter(r)
        for (blockIndex in blocksValues.indices) {
            val blockValue = blocksValues[blockIndex]
            val blockPos = blocksPos[blockIndex]
            val rnode = rnodes[blockValue]

            bitw.write(rnode.v, rnode.bits)
            if ((blockValue and 0x100) != 0) {
                bitw.write(blockPos.toLong(), 12)
                //bitw.finish();
            }
        }
        bitw.flush()

        return r.toByteArray()
    }

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
            if (m != MAGIC) invalidOp("Not a DSC file '$m'")
            if (usize > 0x3_000_000) invalidOp("Too big uncompressed size $usize")
        }

        companion object {
            val MAGIC = "DSC FORMAT 1.00\u0000"

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
        var leafValue: Int = 0
        val childs = IntArray(2)
        var hasChilds: Boolean = false
        var nodeLeft: Int get() = childs[0]; set(v) = run { childs[0] = v }
        var nodeRight: Int get() = childs[1]; set(v) = run { childs[1] = v }
        override fun toString(): String = format("(childs:%08X, leaf:%08X, L:%08X, R:%08X)", hasChilds.toInt(), leafValue, nodeLeft, nodeRight)
    }

    // Check the sizes for the class structs.
    //static assert (Header.sizeof == 0x20, "Invalid size for DSC.Header");
    //static assert (Node.sizeof   == 4*4 , "Invalid size for DSC.Node");

    // Initializes the huffman tree.
    private fun CompressionInit(ihash: Int, src: ByteArray, nodes: Array<Node>) {
        //println("ihash: $ihash")
        val hash = HashUpdater(ihash)
        // Input asserts.
        com.soywiz.korio.lang.assert(src.size >= 0x200)

        // Output asserts.

        val buffer = IntArray(0x200)
        val vector0 = IntArray(0x400)
        var buffer_len = 0

        // Decrypt the huffman header.
        for (n in 0 until buffer.size) {
            val v = (src[n] - (hash.update() and 0xFF)) and 0xFF
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
                nodes[v13a[v13]].hasChilds = false
                nodes[v13a[v13]].leafValue = buffer[buffer_cur + 0] and 0x1FF
                buffer_cur++
                v13++
                group_count++
            }

            val v18 = 2 * (dec0 - group_count)
            if (group_count < dec0) {
                dec0 = (dec0 - group_count)
                for (dd in 0 until dec0) {
                    //println("" + v13 + " : " + v13a[v13])
                    nodes[v13a[v13]].hasChilds = true
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

    private fun CompressionDo(src: ByteArray, dst: ByteArray, nodes: Array<Node>): ByteArray {
        //uint v2 = header.v2;

        var bits = 0
        var nbits = 0
        var s = 0
        var d = 0

        //writefln("--------------------");

        // Check the input and output pointers.
        while ((d < dst.size) && ((s < src.size) || (nbits > 0))) {
            var nentry = 0

            // Look over the tree.
            while (nodes[nentry].hasChilds) {
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
            val info = LOWORD(nodes[nentry].leafValue)

            // Compressed chunk.
            if (HIBYTE(info) == 1) {
                var cvalue = bits ushr (8 - nbits)
                var nbits2 = nbits
                if (nbits < 12) {
                    var bytes = ((11 - nbits) ushr 3) + 1
                    nbits2 = nbits
                    while (bytes-- != 0) {
                        cvalue = src.getu((s++)) + (cvalue shl 8)
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

    private fun extractLevels(freqs: IntArray, levels: IntArray) {
        com.soywiz.korio.lang.assert(freqs.size == levels.size)

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
        if (cnodes.length > 0) {
            cnodes[cnodes.length - 1].propagateLevels()
        }
        //MNode.show(cnodes);

        for (n in 0 until levels.size) levels[n] = 0
        for (node in cnodes) if (node.leaf) levels[node.value] = node.level

        //val mnodes = Array(freqs.size) { MNode() }
        //for (node in cnodes) if (node.leaf) mnodes[node.value] = node
        //
        //assert(mnodes.size == freqs.size)
        //
        //return mnodes.toList()
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

        override fun toString(): String = format("(%08X, %08X, %08X, %08X, [%s, %s])", value, freq, level, encode, (childs[0] != null).toString(), (childs[1] != null).toString())
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

    data class RNode(var v: Long = 0L, var bits: Int = 0) {
        companion object {
            fun iterate(rnodes: Array<RNode>, nodes: Array<DSC.Node>, cnode: Int = 0, level: Int = 0, vv: Long = 0) {
                if (nodes[cnode].hasChilds) {
                    for ((k, ccnode) in nodes[cnode].childs.withIndex()) {
                        iterate(rnodes, nodes, ccnode, level + 1, (vv shl 1) or k.toLong())
                    }
                } else {
                    rnodes[nodes[cnode].leafValue and 0x1FF].apply {
                        this.v = vv
                        this.bits = level
                    }
                }
            }
        }

        override fun toString(): String {
            return if (bits != 0) "%032b".format(v).substr(-bits) else "--"
        }
    }
}

fun ByteArray.compressDsc(level: Int = 9, seed: Int = 0) = DSC.compress(this, level, seed)
fun ByteArray.uncompressDsc() = DSC.decompress(this)