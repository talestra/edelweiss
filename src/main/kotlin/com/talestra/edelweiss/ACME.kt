package com.talestra.edelweiss

import com.soywiz.korio.lang.LATIN1
import com.soywiz.korio.lang.UTF8
import com.soywiz.korio.lang.toString
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.quoted
import java.io.File

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
            com.soywiz.korio.lang.assert(pos >= 0)
            e.header(line[0 until pos].trim())
            e.text = line[pos + 1 until line.length].trimEnd()
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
                    val add_text = stripslashes(substr(line, 4)).trimEnd()
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

    fun writePo(s: SyncStream, file: String = "unknown") {

        //#: lib/error.c:116
        //msgid "Unknown system error"
        //msgstr "Error desconegut del sistema"
    }

    fun SyncStream.writeLine(str: String) = writeString("$str\n")

    fun generatePo(file: String) = MemorySyncStreamToByteArray {
        writeLine("# Comments for '$file'")
        writeLine("msgid \"\"")
        writeLine("msgstr \"\"")
        writeLine("\"Project-Id-Version: \\n\"")
        writeLine("\"POT-Creation-Date: \\n\"")
        writeLine("\"PO-Revision-Date: \\n\"")
        writeLine("\"Last-Translator: \\n\"")
        writeLine("\"Language-Team: \\n\"")
        writeLine("\"MIME-Version: 1.0 \\n\"")
        writeLine("\"Content-Type: text/plain; charset=utf-8 \\n\"")
        writeLine("\"Content-Transfer-Encoding: 8bit \\n\"")
        writeLine("\"Language: es\\n\"")
        writeLine("")
        for (k in entries.keys.sorted()) {
            val t = entries[k]
            val text = t!!.text
            writeLine("#: $file:$k\n")
            writeLine("msgid ${text.quoted}")
            writeLine("msgstr ${"".quoted}")
            writeLine("")
        }
    }

    override fun toString(): String {
        return entries.map { it.toString() }.joinToString("")
    }
}