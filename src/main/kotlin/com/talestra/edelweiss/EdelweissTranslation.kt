package com.talestra.edelweiss

import com.soywiz.korim.format.readBitmapNoNative
import com.soywiz.korio.Korio
import com.soywiz.korio.async.toList
import com.soywiz.korio.vfs.VfsFile
import com.soywiz.korio.vfs.uniVfs

object EdelweissTranslation {
    fun processScript(name: String, data: ByteArray): ByteArray {
        if (name != "00106") return data
        val script = BSS.load(DSC.decompressIfRequired(data))
        fun translate(v: Any?): Any? {
            if (v == "\"Hey!\"") {
                return "\"¡Oye!\""
            }
            return v
        }

        for (s in script) {
            s.args = ArrayList(s.args.map { translate(it) })
        }

        return script.serialize()
        //return DSC.decompressIfRequired(data)
    }

    suspend fun repackArc(arc: VfsFile, transform: suspend (name: String, data: ByteArray) -> ByteArray) {
        val arcBak = arc.appendExtension("bak")
        if (!arcBak.exists()) {
            arc.copyTo(arcBak)
        }
        val files = arcBak.openAsArc()
        ARC.build(arc, files.list().toList().map { it.basename }, level = -1) {
            transform(it, files[it].readBytes())
        }
    }

    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val gameFolder = "../game".uniVfs

        repackArc(gameFolder["sysgrp.arc"]) { name, data ->
            if (name == "SGTitle000000") {
                UncompressedImage.save(gameFolder["SGTitle000000.png"].readBitmapNoNative().toBMP32())
            } else {
                //println("$name...")
                //DSC.decompressIfRequired(data)
                data
            }
        }

        /*
        defaultImageFormats.registerStandard()
        val bmp = gameFolder["Graphic/CVTD/warning_2_n.png"].readBitmapNoNative().toBMP32()
        gameFolder["Graphic/CVTD/warning_2_n"].writeBytes(UncompressedImage.save(bmp))
        */

        return@Korio

        val scriptArc = gameFolder["data01000.arc"]
        //val scriptArcBak = scriptArc.appendExtension("bak")
        //if (!scriptArcBak.exists()) scriptArc.copyTo(scriptArcBak)


        //val arc = scriptArcBak.openAsArc()
        val arc = scriptArc.openAsArc()
        val originalScripts = arc.list().toList().map { it.basename to it.readBytes() }.toMap()
        val processedScripts = originalScripts.map { it.key to processScript(it.key, it.value) }.toMap()

        val outputFolder = gameFolder["Script/CVTD"]
        outputFolder.mkdirs()
        //ARC.build(scriptArc, processedScripts, level = 0)
        var totalTexts = ""
        for ((name, data) in processedScripts) {
            //val script = BSS.load(data)
            //val acme = BssTranslation.extract(script)
            //val texts = acme.entries.map { it.value.text }
            //totalTexts += texts.joinToString("\n")

            //println(texts)
            outputFolder[name].writeBytes(data)
        }

        println("Characters: " + totalTexts.length)
        val words = totalTexts.split(Regex("\\b")).map { it.trim() }.filter { it.isNotEmpty() }
        println("Words: " + words.size)

        //gameFolder["Script/CVTD/00106"].ensureParents().writeBytes(script.serialize())
        //gameFolder["Script/CVTD/00106"].ensureParents().writeBytes(script.serialize().dscCompress())

        //ARC.build(gameFolder["data01001.arc"], mapOf(
        //        "00106" to script.serialize()
        //))

        //script.map { it.args.map { translate(it) } }
        //println(script)

        //println(arc.list().map { it.basename }.toList())
        /*
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
        */
    }
}