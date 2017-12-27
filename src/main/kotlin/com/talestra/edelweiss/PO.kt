package com.talestra.edelweiss

import com.soywiz.korio.error.invalidOp
import com.soywiz.korio.lang.UTF8
import com.soywiz.korio.lang.toByteArray
import com.soywiz.korio.util.quoted
import com.soywiz.korio.util.substr
import com.soywiz.korio.util.unquote

class PO(val entries: List<Entry>) {
    class Entry(
            val msgid: String = "",
            val msgstr: String = "",
            val comments: List<String>
    ) {
        val references by lazy {
            comments.filter { it.trim().startsWith(':') }.map { it.trim().substr(1).trim() }
        }

        private fun String.split2(char: Char): List<String> {
            val parts = this.split(char)
            return parts.withIndex().map { if (it.index < parts.size - 1) "${it.value}$char" else it.value }
        }

        private fun ssplit(kind: String, text: String): List<String> {
            val parts = text.split2('\n')
            if (parts.size == 1) {
                return listOf("$kind ${parts[0].quoted}")
            } else {
                return listOf("$kind \"\"") + parts.filter { it.isNotEmpty() }.map { it.quoted }
            }
        }

        fun toStringList(): List<String> {
            return comments.map { "#$it" } + ssplit("msgid", msgid) + ssplit("msgstr", msgstr) + listOf("")
        }

        override fun toString(): String = "'$msgid' -> '$msgstr'"
    }

    companion object {
        fun load(lines: List<String>): PO {
            val comments = arrayListOf<String>()
            val strs = hashMapOf<String, String>()
            var kind = ""
            val entries = arrayListOf<Entry>()

            fun flush() {
                if ("msgid" !in strs) return
                entries += Entry(
                        msgid = strs["msgid"]!!,
                        msgstr = strs["msgstr"] ?: "",
                        comments = comments.toList()
                )
                comments.clear()
                strs.clear()
            }

            for ((ln0, line) in lines.withIndex()) {
                val ln = ln0 + 1
                val l = line.trim()
                if (l.isEmpty()) continue
                if (l.startsWith("#")) {
                    flush()
                    comments.add(l.substr(1).trimEnd())
                } else if (l.startsWith("msgid") || l.startsWith("msgstr")) {
                    kind = if (l.startsWith("msgid")) "msgid" else "msgstr"
                    if (kind == "msgid") flush()
                    val res = l.substr(kind.length).trim()
                    if (res.startsWith('"') && res.endsWith('"')) {
                        strs[kind] = res.unquote()
                    } else {
                        invalidOp("Invalid $kind at $ln")
                    }
                } else if (l.startsWith('"')) {
                    if (l.startsWith('"') && l.endsWith('"')) {
                        strs[kind] = strs.getOrElse(kind) { "" } + l.unquote()
                    } else {
                        invalidOp("Invalid $kind at $ln")
                    }
                } else {
                    invalidOp("Not a string line: '$l'")
                }
            }

            flush()

            return PO(entries.toList())
        }

        fun save(po: PO): List<String> {
            return po.entries.flatMap { it.toStringList() }
        }
    }

    fun save() = PO.save(this)

    fun saveToString() = save().joinToString("\n")

    fun saveToByteArray() = saveToString().toByteArray(UTF8)
}