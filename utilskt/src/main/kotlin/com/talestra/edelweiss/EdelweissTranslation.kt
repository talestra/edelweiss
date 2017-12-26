package com.talestra.edelweiss

import com.soywiz.korio.Korio
import com.soywiz.korio.stream.openSync
import com.soywiz.korio.vfs.uniVfs

object EdelweissTranslation {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val gameFolder = "../game".uniVfs
        val arc = gameFolder["data01000.arc"].openAsArc()
        println("Extracting files...")
        for (file in arc) {
            println(file.basename)
            val target = gameFolder[file.basename]
            val targetBak = target.appendExtension("bak")
            if (!target.exists()) {
                file.copyTo(target)
            }
            if (!targetBak.exists()) {
                target.copyTo(targetBak)
            }
        }
        println("Uncompressing files...")
        for (file in arc) {
            println(file.basename)
            val target = gameFolder[file.basename]
            val source = target.appendExtension("bak")
            val poFile = target.appendExtension("po")
            val scriptBytes = DSC.decompressIfRequired(source.readAll())
            target.writeBytes(scriptBytes)
            val acme = BssTranslation.extract(BSS.load(scriptBytes.openSync()))
            poFile.writeBytes(acme.generatePo(file.basenameWithoutExtension))
        }
    }
}