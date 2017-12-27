package com.talestra.edelweiss

import com.soywiz.korio.error.invalidOp
import com.soywiz.korio.lang.Charset
import com.soywiz.korio.lang.LATIN1
import com.soywiz.korio.lang.toByteArray
import com.soywiz.korio.stream.*
import java.io.File
import kotlin.math.min

open class BSS(var charset: Charset) {
    companion object : BSS(LATIN1)

    data class Pointer(val addr: Int)

    object Opcodes {
        const val PUSH_INT = 0x0_00     // 000 - PUSH_INT <INT:INT>?
        const val PUSH_PTR = 0x0_01
        const val PUSH_STR = 0x0_03     // 003 - PUSH_STRING <STR:STR>
        const val STACK = 0x0_3F        // 03F - STACK_SIZE <INT:???>
        const val SCRIPT_LINE = 0x0_7F  // 07F - SCRIPT_LINE <STR:FILE> <INT:LINE>
        const val SCRIPT_CALL = 0x0_F0  // 0F0 - CALL_SCRIPT
        const val TEXT_PUT = 0x1_40     // 140 - TEXT_PUT text, title, i0, i1, i2
        const val TEXT_SIZE = 0x1_4D
        const val AUDIO = 0x1_80
    }

    class OP(var type: Int, var ori_pos: Int, var args: ArrayList<Any?>) {
        val byteSize: Int get() = 4 + args.size * 4

        fun int(index: Int) = args[index] as Int
        fun str(index: Int) = args[index] as? String?

        override fun toString(): String {
            if (type == Opcodes.SCRIPT_LINE) return "\n%s_%d:".format(args[0], args[1])
            var r = when (type) {
                Opcodes.PUSH_INT -> "PUSH_INT"
                Opcodes.PUSH_PTR -> "PUSH_PTR"
                Opcodes.PUSH_STR -> "PUSH_STR"
                Opcodes.STACK -> "STACK"
                Opcodes.TEXT_PUT -> "TEXT_PUT"
                Opcodes.TEXT_SIZE -> "TEXT_SIZE"
                else -> format("OP_%03X", type)
            }
            r += " "
            for (n in 0 until args.size) {
                if (n != 0) r += ", "
                val arg = args[n]
                r += when (arg) {
                    null -> "null"
                    is String -> "'$arg'"
                    is Int -> "$arg"
                    is Pointer -> "$arg"
                    else -> invalidOp
                }
            }
            r += ""
            return r
        }

        fun print() {
            println(toString())
        }
    }

    fun load(name: String) = load(File(name).readBytes().openSync())

    fun load(data: ByteArray): List<OP> = load(data.openSync())

    fun load(s: SyncStream): List<OP> {
        val ops = arrayListOf<OP>()
        var end = s.length.toInt()
        fun read() = s.readS32_le()
        while (!s.eof) {
            //println("${s.position}-${s.length}-${s.available}-${s.available % 4}")
            val op_pos = s.position.toInt()
            if (op_pos >= end) break
            val op_type = read()
            val args = arrayListOf<Any?>()

            fun pushInt() = args.add(read())
            fun pushPtr() = args.add(Pointer(read()))

            fun pushStr(): String {
                val pos = read()
                //writefln("    : %08X", pos);
                end = min(end, pos) // Delimit opcodes!
                val v = s.sliceWithStart(pos.toLong()).readStringz(charset)
                //writefln("      '%s'", v);
                args.add(v)
                return v
            }

            //writefln("%08X", op.type);
            when (op_type) {
                Opcodes.PUSH_INT -> pushInt()
                Opcodes.PUSH_PTR -> pushPtr()
                0x0_02 -> Unit // ??
                Opcodes.PUSH_STR -> pushStr()
                0x0_04 -> pushInt() // ??
                0x0_09 -> pushInt() // PUSH_??
            //0x0_10 -> { pushInt(); pushInt(); } // ??
            //0x0_11 -> {
            //    pushInt();
            //    int size = pushInt();
            //    string_ptr = size + (op_ptr - op_start);
            //    op_end = cast(uint *)((cast(ubyte *)op_ptr) + size);
            //}
                0x0_19 -> pushInt() // ??
                Opcodes.STACK -> pushInt()
                Opcodes.SCRIPT_LINE -> run { pushStr(); pushInt() }
                Opcodes.SCRIPT_CALL -> Unit
                0x0_1E -> Unit
                0x0_20 -> Unit
                0x0_21 -> { // UNK_STACK_OP_22
                    pushInt()
                    pushInt()
                }
                0x0_22 -> Unit // UNK_STACK_OP_22
                Opcodes.AUDIO -> Unit
                Opcodes.TEXT_SIZE -> Unit
                Opcodes.TEXT_PUT -> Unit
                else -> Unit
            }

            val op = OP(op_type, op_pos, args)
            ops.add(op)
            //println(op)
        }
        return ops
    }

    fun save(ops: List<OP>): ByteArray {
        val table = LinkedHashMap<String, Int>()
        val ins = arrayListOf<Int>()
        val strs = ByteArrayBuilder()
        val str_start: Int = ops.map { it.byteSize }.sum()
        var str_pos: Int = str_start

        fun putText(text: String): Int = table.getOrPut(text) {
            val pos = str_pos
            val bytes = (text + "\u0000").toByteArray(charset)
            strs.append(bytes)
            str_pos += bytes.size
            pos
        }

        for (op in ops) {
            ins += op.type
            for (arg in op.args) {
                when (arg) {
                    null -> invalidOp
                    is String -> {
                        ins += putText(arg)
                        if (op.type == Opcodes.SCRIPT_LINE) putText("bss.h")
                    }
                    is Int -> ins += arg
                    is Pointer -> ins += arg.addr
                    else -> invalidOp
                }
            }
            //writefln(op);
        }

        val s = MemorySyncStream()
        for (i in ins) s.write32_le(i)
        s.writeBytes(strs.toByteArray())
        return s.toByteArray()
    }

    fun insert(pos: Int, ops: List<OP>, new_ops: List<OP>) = ops.subList(0, pos) + new_ops + ops.subList(pos, ops.size)

    fun dump(ops: List<OP>) {
        var pos = 0
        for ((k, op) in ops.withIndex()) {
            writefln("%08d: %s\n", pos, op.toString())
            pos += op.byteSize
        }
    }
}

fun List<BSS.OP>.serialize() = BSS.save(this)