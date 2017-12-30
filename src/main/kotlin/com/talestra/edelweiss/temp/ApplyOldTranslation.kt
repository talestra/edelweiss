package com.talestra.edelweiss.temp

import com.soywiz.korio.Korio
import com.soywiz.korio.async.filter
import com.soywiz.korio.async.toList
import com.soywiz.korio.lang.UTF8
import com.soywiz.korio.vfs.localCurrentDirVfs
import com.talestra.edelweiss.ACME
import com.talestra.edelweiss.PO

object ApplyOldTranslation {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        for (acmeFile in localCurrentDirVfs["old_texts"].list().filter { it.extensionLC == "txt" }.toList().sortedBy { it.basename }) {
            val poFile = localCurrentDirVfs["translation"][acmeFile.pathInfo.basenameWithoutExtension + ".po"]
            val acme = ACME().parseForm2(acmeFile.readAsSyncStream())
            val po = PO.load(poFile.readString().lines())

            val translations = acme.entries.map { it.value.original to it.value }.toMap() + mapOf("" to acme.documentEntry)

            val translatedPo = PO(po.entries.map {
                val ref = it.references.firstOrNull()
                //val id = ref?.split(":", limit = 2)?.lastOrNull()?.toInt()
                val translation = translations[it.msgid]
                if (translation != null) {
                    it.copy(msgstr = if (translation.text.isNotBlank()) translation.text else it.msgstr, comments = (it.comments + translation.comments.map { " " + it.trim() }).distinct())
                } else {
                    it
                }
            })

            poFile.writeString(PO.save(translatedPo).joinToString("\n"), UTF8)

            //println(translatedPo.entries)

            //println(acmeFile)
            //println(poFile)
            //break
        }
        //val acme = ACME().parseForm2("old_texts/00106.txt")
        //for (e in acme.entries) {
        //    println(e)
        //}
    }
}