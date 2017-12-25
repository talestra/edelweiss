package com.talestra.edelweiss

import com.soywiz.korio.error.ignoreErrors
import com.soywiz.korio.lang.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.substr
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess

// Version of the utility.
const private val _version = "0.3"

/*
OPCODES:

000 - PUSH_INT <INT:INT>?
003 - PUSH_STRING <STR:STR>
03F - STACK_SIZE <INT:???>
07F - SCRIPT_LINE <STR:FILE> <INT:LINE>

0F0 - CALL_SCRIPT

140 - PUT_TEXT text, title, i0, i1, i2

*/

class BSS {
    class OP(var type: Int = 0, var ori_pos: Int = 0) {
        enum class TYPE { INT, STR, PTR }

        val i = arrayListOf<Int>()
        val s = arrayListOf<String?>()
        val t = arrayListOf<TYPE>()

        fun push(v: Int): OP = run { i += v; s.add(null); t += TYPE.INT; return this; }
        fun push(v: String?): OP = run { i += 0; s += v; t += TYPE.STR; return this; }
        fun pushPTR(v: Int): OP = run { i += v; s.add(null); t += TYPE.PTR; return this; }
        val length: Int get() = i.size

        override fun toString(): String {
            if (type == 0x7F) return "\n%s_%d:".format(s[0], i[1])
            var r = when (type) {
                0x0_00 -> "PUSH_INT"
                0x0_01 -> "PUSH_PTR"
                0x0_03 -> "PUSH_STR"
                0x0_3F -> "STACK"
                0x1_40 -> "TEXT_PUT"
                0x1_4D -> "TEXT_SIZE"
                else -> format("OP_%03X", type)
            }
            r += " "
            for (n in 0 until length) {
                if (n != 0) r += ", "
                r += if (s[n] != null) ("'" + s[n] + "'") else format("%d", i[n])
            }
            r += ""
            return r
        }

        fun popi(): Int {
            if (i.size <= 0) return 0
            val r = i[i.size - 1]
            val tmp = (length - 1)
            s.length = tmp
            t.length = tmp
            i.length = tmp
            return r
        }

        val size: Int get() = 4 + length * 4

        fun print() {
            println(toString())
        }
    }

    var ops = arrayListOf<OP>()
    fun parse(name: String) {
        parse(File(name).readBytes().openSync())
    }

    fun parse(s: SyncStream) {
        val s = s.readAvailable().openSync()
        var end = s.length.toInt()
        fun read() = s.readS32_le()
        while (!s.eof) {
            //println("${s.position}-${s.length}-${s.available}-${s.available % 4}")
            val op_pos = s.position.toInt()
            if (op_pos >= end) break
            val op = OP(read(), op_pos)

            fun pushInt() = op.push(read())
            fun pushPtr() = op.pushPTR(read())

            fun pushStr(): String {
                val pos = read()
                //writefln("    : %08X", pos);
                end = min(end, pos)
                val v = s.sliceWithStart(pos.toLong()).readStringz()
                //writefln("      '%s'", v);
                op.push(v)
                return v
            }

            //writefln("%08X", op.type);
            when (op.type) {
                0x0_00 -> pushInt() // PUSH_INT
                0x0_01 -> pushPtr() // PUSH_ADDR?
                0x0_02 -> Unit // ??
                0x0_03 -> pushStr() // PUSH_STRING
                0x0_04 -> pushInt() // ??
                0x0_09 -> pushInt() // PUSH_??
                0x0_19 -> pushInt() // ??
                //0x0_10 -> { pushInt(); pushInt(); } // ??
                //0x0_11 -> {
                //    pushInt();
                //    int size = pushInt();
                //    string_ptr = size + (op_ptr - op_start);
                //    op_end = cast(uint *)((cast(ubyte *)op_ptr) + size);
                //}
                0x0_3F -> pushInt()
                0x0_7F -> { // SCRIPT_LINE
                    pushStr()
                    pushInt()
                }
                0x0_F0 -> Unit // SCRIPT_CALL
                0x0_1E -> Unit
                0x0_20 -> Unit
                0x0_21 -> { // UNK_STACK_OP_22
                    pushInt()
                    pushInt()
                }
                0x0_22 -> Unit // UNK_STACK_OP_22
                0x1_80 -> Unit // AUDIO
                0x1_4D -> Unit // TEXT_SIZE
                0x1_40 -> Unit // TEXT_WRITE
                else -> Unit
            }

            ops.add(op)
            //println(op)
        }
    }

    fun serialize(): ByteArray {
        val charset = UTF8
        val table = LinkedHashMap<String, Int>()
        val ins = arrayListOf<Int>()
        val str = ByteArrayBuilder()
        var str_start: Int = 0
        for (op in ops) str_start += op.size

        for (op in ops) {
            ins += op.type
            for (n in 0 until op.length) {
                if (op.s[n] == null) {
                    ins += op.i[n]
                } else {
                    val s = op.s[n]
                    if (s !in table) {
                        table[s!!] = str_start + str.size
                        str.append((s + "\u0000").toByteArray(charset))
                    }
                    ins += table[s]!!
                }
            }
            //writefln(op);
        }

        return ByteArray(ins.size) { ins[it].toByte() } + str.toByteArray()
    }

    fun write(name: String) {
        val s = MemorySyncStream()
        write(s)
        s.close()
        File(name).writeBytes(s.toByteArray())
    }

    fun write(s: SyncStream) {
        s.writeBytes(serialize())
    }

    fun dump() {
        var pos = 0
        for ((k, op) in ops.withIndex()) {
            writefln("%08d: %s\n", pos, op.toString())
            pos += op.size
        }
    }

    fun insert(pos: Int, new_ops: ArrayList<OP>) {
        ops = ArrayList(ops.subList(0, pos) + new_ops + ops.subList(pos, ops.size))
    }

    fun patchStrings(acme: ACME) {
        data class PATCH(
                var pos: Int = 0,
                val ops: ArrayList<OP> = arrayListOf()
        )

        val patches = arrayListOf<PATCH>()
        var line = 0
        var line_pos = 0
        val pushes = arrayListOf<OP>()
        var sstack = OP(0)
        var font_width = 22
        var font_height = 22
        var last_op_type: Int = 0
        var changed_size = false
        for ((pos, op) in ops.withIndex()) {
            when (op.type) {
                0x7F -> { // SCRIPT_LINE
                    changed_size = (last_op_type == 0x1_4D)
                    line = op.i[1]
                    line_pos = pos + 1
                    sstack = OP(-1)
                    pushes.clear()
                }
                0x00 -> {
                    sstack.push(op.i[0]); pushes += op
                }
                0x03 -> {
                    sstack.push(op.s[0]); pushes += op
                }
                0x3F -> {
                    //writefln(op);
                }
                0x1_40 -> { // TEXT_WRITE
                    //writefln("TEXT_WRITE");
                    if (acme.has(line)) {
                        var text = acme[line]!!.text

                        if (sstack.s[1] != null) {
                            sstack.s[1] = sstack.s[1]
                        }
                        //writefln("::%s::%s::", sstack.s[1], text);

                        // Has title.
                        if ((sstack.s[1] != null) && sstack.s[1]!!.strip().length != 0) {
                            val tt = explode("\n", text, 2)
                            var title = tt[0].strip()
                            text = if (tt.length >= 2) tt[1] else ""
                            assert(title.length > 2) { format("ID/Line(@%d): Title length > 2", line) }
                            assert(title[0] == '{') { format("ID/Line(@%d): Line doesn't start by '{'", line) }
                            assert(title[title.length - 1] == '}') { format("ID/Line(@%d): Line end by '}'", line) }
                            title = title[1 until title.length - 1]
                            //while (title.length < 5) title += " ";
                            // Ignore current title, and use the original one.
                            // Another title won't work on Edelweiss.
                            title = sstack.s[1]!!
                            //writefln(pushes[1]);
                            pushes[1].s[0] = title
                        }
                        // Has text.
                        if (sstack.s[0] != null) {
                            var ttext = text.stripr()
                            //writefln(pushes[0]);
                            ttext = ttext.replace("\r", "").replace("\n ", " ").replace(" \n", " ").replace("\n", " ")
                            pushes[0].s[0] = ttext
                            //pushes[0].s[0] = ttext;

                            val calc_lines = (ttext.length / 42) + 1

                            if ((font_height <= 22) && (font_height >= 19)) {
                                var calc_height = 22
                                if (ttext.length <= 44 * 3) {
                                    calc_height = 22
                                } else if (ttext.length <= 44 * 4) {
                                    calc_height = 20
                                } else if (ttext.length < 44 * 5) {
                                    calc_height = 19
                                }
                                //int calc_height = 22 - cast(int)(1.1 * (calc_lines - 2));
                                //calc_height = max(19, min(calc_height, 22));
                                if (calc_height != font_height) {
                                    // 2, font_width, font_height, 0
                                    val patch = PATCH()
                                    run {
                                        patch.pos = line_pos
                                        patch.ops += OP(0x00).push(2)
                                        patch.ops += OP(0x00).push(calc_height)
                                        patch.ops += OP(0x00).push(calc_height)
                                        patch.ops += OP(0x00).push(0)
                                        patch.ops += OP(0x3F).push(4)
                                        patch.ops += OP(0x1_4D)
                                    }
                                    patches += patch
                                    font_height = calc_height
                                }
                            }
                        }
                    }
                }
                0x0_22 -> {
                    val a = sstack.popi()
                    val b = sstack.popi()
                    pushes.length = pushes.length - 1
                    pushes.length = pushes.length - 1
                    sstack.push(a * b)
                }
                0x1_4D -> {
                    //writefln("TEXT_SIZE: %s", sstack);
                    font_width = sstack.i[0]
                    font_height = sstack.i[1]
                }
                else -> {

                }
            }
            last_op_type = op.type
        }

        var disp = 0
        for (patch in patches) {
            insert(patch.pos + disp, patch.ops)
            disp += patch.ops.length
        }

        // Fix pointers.
        var size = 0
        val translate = LinkedHashMap<Int, Int>()
        for (op in ops) {
            translate[op.ori_pos] = size
            //writefln("%d, %d", op.ori_pos, size);
            size += op.size
        }
        var pos = 0
        for (op in ops) {
            pos += op.size
            //if (op.type == 0x11) op.i[1] = size - pos;
            for ((k, t) in op.t.withIndex()) {
                try {
                    if (t == OP.TYPE.PTR) {
                        op.i[k] = translate[op.i[k]]!!
                        //writefln("Update!");
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun extract(): ACME {
        val acme = ACME()
        var sstack = OP(0)
        var line: Int = 0
        var line_pos = 0

        for ((pos, op) in ops.withIndex()) {
            when (op.type) {
                0x7F -> { // SCRIPT_LINE
                    line = op.i[1]
                    line_pos = pos + 1
                    sstack = OP(-1)
                }
                0x00 -> {
                    sstack.push(op.i[0])
                }
                0x03 -> {
                    //println("03: ${op.s[0]}");
                    sstack.push(op.s[0])
                }
                0x1_40 -> { // TEXT_WRITE
                    //println("TEXT_WRITE");
                    var r: String = ""

                    //val title = sstack.s.getOrNull(sstack.s.size - 2)?.trim() ?: ""
                    //if (title.isNotEmpty()) r += "{$title}\n"
                    //r += sstack.s.getOrNull(sstack.s.size - 1) ?: ""

                    val title = sstack.s.getOrNull(1)?.trim() ?: ""
                    if (title.isNotEmpty()) r += "{$title}\n"
                    r += sstack.s.getOrNull(0) ?: ""

                    //println(" ## $title");
                    acme.add(line, r)
                }
                else -> Unit
            }
        }

        return acme
    }
}

fun SyncStream.readLine(charset: Charset = UTF8): String {
    val out = ByteArrayBuilder()
    while (!eof) {
        val b = readU8()
        if (b == '\n'.toInt()) break
        out += b.toByte()
    }
    return out.toString(charset)
}

class ACME {
    class Entry {
        var id: Int = 0
        var text: String = ""
        val attribs = LinkedHashMap<String, String>()
        fun header(header: String) {
            val r = Regex("^(\\d+)")
            val ss = r.find(header)!!
            id = ss.groupValues[1].toInt()
            //writefln(header);
        }

        override fun toString(): String {
            return ("## POINTER $id\n" + text + "\n\n")
        }
    }

    fun add(id: Int, text: String): Entry {
        val e = Entry()
        e.text = text
        e.id = id
        entries[id] = e
        return e
    }

    val entries = LinkedHashMap<Int, Entry>()
    fun parse(name: String) {
        parse(File(name).readBytes().openSync())
    }

    fun has(idx: Int): Boolean = idx in entries

    operator fun get(idx: Int): Entry? = entries[idx]
    val length: Int get() = entries.length

    fun parse(s: SyncStream) {
        entries.clear()
        s.position = 0;
        val data = s.readBytes(s.length.toInt())
        var ss = data.toString(UTF8).split("## POINTER ")
        ss = ss.drop(1)
        for (line in ss) {
            val e = Entry()
            val pos = line.indexOf('\n')
            assert(pos >= 0)
            e.header(line[0 until pos].strip())
            e.text = line[pos + 1 until line.length].stripr()
            entries[e.id] = e
        }
    }

    fun parseForm2(filename: String, lang: String = "es") {
        val s = File(filename).readBytes().openSync()
        parseForm2(s, lang)
        s.close()
    }

    fun parseForm2(s: SyncStream, lang: String = "es") {
        var id: Int = 0
        entries.clear()
        var e = Entry()

        while (!s.eof) {
            val line = s.readLine(LATIN1)
            val c = line.getOrElse(0) { '\u0000' }
            when (c) {
                '#' -> Unit // Comment
                '@' -> { // ID
                    e = Entry()
                    e.id = line[1 until line.length].toInt()
                    e.text = ""
                    entries[e.id] = e
                }
                '<' -> { // Text
                    val add_text = stripslashes(substr(line, 4)).stripr()
                    if (substr(line, 0, 4) == "<$lang>") {
                        if (add_text.isNotEmpty()) e.text = add_text
                    } else if (e.text.isNotEmpty()) {
                        e.text = add_text
                    }
                }
                else -> Unit // Ignore.
            }
        }
    }

    fun writeForm2(filename: String, file: String = "unknown") {
        val s = MemorySyncStream();
        run {
            writeForm2(s, file)
        }
        s.close()
        File(filename).writeBytes(s.toByteArray())
    }

    fun writeForm2(s: SyncStream, file: String = "unknown") {
        s.writeString("# Comments for '$file'\n")
        s.writeString("\n")
        for (k in entries.keys.sorted()) {
            val t = entries[k]
            s.writeString("@$k\n")
            s.writeString("<en>${addslahses(t!!.text)}\n")
            s.writeString("<es>\n")
            s.writeString("\n")
        }
    }

    override fun toString(): String {
        return entries.map { it.toString() }.joinToString("")
    }
}

private fun patch(game_folder: String, acme_folder: String) {
    val script_folder_in = "$game_folder/data01000.arc.d"
    val script_folder_out = "$game_folder/Script/CVTD"

    val acme = ACME()
    val bss = BSS()

    println("Patch all:")
    println(" - script_folder_in : $script_folder_in")
    println(" - script_folder_out: $script_folder_out")
    println(" - acme_folder      : $acme_folder")

    ignoreErrors { File(game_folder + "/Script").mkdirs() }
    ignoreErrors { File(game_folder + "/Script/CVTD").mkdirs(); }

    for (file in listdir(script_folder_in)) {
        println(file)
        val file_in = "$script_folder_in/$file"
        val file_out = "$script_folder_out/$file"
        val acme_in = "$acme_folder/$file.txt"

        println("  ACME parsing...")
        acme.parseForm2(acme_in)
        println("  BSS parsing...")
        bss.parse(file_in)
        println("  BSS patching...")
        bss.patchStrings(acme)
        writefln("  BSS writting...")
        bss.write(file_out)
        //bss.dump();
    }
}

fun extract_all2(game_folder: String, acme_folder: String) {
    val script_folder_in = game_folder + "/data01000.arc.d"

    val bss = BSS()

    println("Extract all:")
    println(" - script_folder: $script_folder_in")
    println(" - acme_folder  : $acme_folder")

    val file_list = File(script_folder_in).list().sorted()
    if (file_list.isNotEmpty()) {
        for (file in file_list) {
            if (file[0] == '.') continue
            println(file)
            bss.parse(script_folder_in + "/" + file)
            val acme = bss.extract()
            acme.writeForm2("$acme_folder/$file.txt", file)
        }
    } else {
        println("No files detected.")
    }
}

fun main(args2: Array<String>) {
    val args = arrayOf("script.exe") + args2
    fun show_help() {
        println("Ethornell script utility $_version - soywiz - 2009 - Build $__TIMESTAMP__")
        println("Knows to work with English Shuffle! and Edelweiss with Ethornell 1.69.140")
        println()
        println("script <command> <game_folder> <text_folder>")
        println()
        println("  -x[1,3]  Extracts texts from a folder with scripts.")
        println("  -p       Patches a folder with scripts with modified texts.")
        println()
        println("  -h       Show this help")
    }

    try {
        if (args.size < 2) throw ShowHelpException()

        when (args[1].substr(0, 2)) {
            "-x" -> extract_all2(args[2], args[3]) // Game folder -> Text folder
            "-p" -> patch(args[2], args[3]) // Game folder <- Text folder
        // Unknown command.
            else -> throw ShowHelpException("Unknown command '${args[1]}'")
        }
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

    exitProcess(0)
}

