package com.talestra.edelweiss.scripts

import com.soywiz.korio.Korio
import com.soywiz.korio.async.filter
import com.soywiz.korio.vfs.localCurrentDirVfs
import com.talestra.edelweiss.PO

object ReportTranslation {
    fun String.countWords() = this.split(Regex("\\b+")).map { it.trim() }.filter { it.isNotEmpty() }.count()

    @JvmStatic
    fun main(args: Array<String>) = Korio {
        var en = ""
        var es = ""
        var totalCount = 0
        var translatedCount = 0
        for (poFile in localCurrentDirVfs["translation"].list().filter { it.extensionLC == "po" }) {
            val po = PO.load(poFile.readString().lines())
            for (entry in po.entries) {
                en += " " + entry.msgid
                totalCount++
                if (entry.msgstr.isNotEmpty()) {
                    translatedCount++
                    es += " " + entry.msgstr
                }
            }
        }

        val enWords = en.countWords()
        val esWords = es.countWords()

        println("Estado actual:")
        println("")
        println("Caracteres en inglés: ${en.length}")
        println("Palabras en inglés: $enWords")
        println("Textos en inglés: $totalCount")
        println("Palabras por texto en inglés: ${enWords.toDouble() / totalCount.toDouble()}")
        println("")
        println("Caracteres traducidos: ${es.length}")
        println("Palabras traducidas: ${es.countWords()}")
        println("Textos traducidos: $translatedCount")
        println("Palabras por texto traducidos: ${esWords.toDouble() / translatedCount.toDouble()}")
        println("")
        println("Progreso: %.2f%%".format((translatedCount.toDouble() / totalCount.toDouble()) * 100))
    }
}