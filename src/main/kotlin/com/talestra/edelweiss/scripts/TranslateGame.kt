package com.talestra.edelweiss.scripts

import com.soywiz.korim.format.defaultImageFormats
import com.soywiz.korim.format.readBitmapNoNative
import com.soywiz.korim.format.registerStandard
import com.soywiz.korio.Korio
import com.soywiz.korio.async.toList
import com.soywiz.korio.vfs.VfsFile
import com.soywiz.korio.vfs.localCurrentDirVfs
import com.talestra.edelweiss.*

object TranslateGame {
    lateinit var gameDir: VfsFile
    lateinit var translationDir: VfsFile

    @JvmStatic
    fun main(args: Array<String>) = Korio {
        defaultImageFormats.registerStandard()
        gameDir = localCurrentDirVfs["game"].jail()
        translationDir = localCurrentDirVfs["translation"].jail()

        translateSysGrp()
        translateGraphic()
        translateScript()
        println("Done!")
    }

    suspend fun VfsFile.getBak(): VfsFile {
        val bak = this.appendExtension("bak")
        if (!bak.exists()) this.copyTo(bak)
        return bak
    }

    suspend fun repackArc(arc: VfsFile, transform: suspend (name: String, data: ByteArray) -> ByteArray) {
        val arcBak = arc.getBak()
        val files = arcBak.openAsArc()
        ARC.build(arc, files.list().toList().map { it.basename }, level = -1) {
            transform(it, files[it].readBytes())
        }
    }

    suspend fun translateImage(patchDir: VfsFile, name: String, data: suspend () -> ByteArray): ByteArray? {
        //println(patchDir.absolutePath)

        val replaceImageFile = patchDir["$name.png"]
        val patchImageFile = patchDir["$name.patch.png"]

        return when {
            replaceImageFile.exists() -> {
                println(" - Replaced $name")
                EdelweissImage.save(replaceImageFile.readBitmapNoNative())
            }
            patchImageFile.exists() -> {
                val image = EdelweissImage.load(data()).toBMP32()
                image.draw(patchImageFile.readBitmapNoNative().toBMP32(), 0, 0)
                println(" - Patched  $name")
                EdelweissImage.save(image)
            }
            else -> null
        }
    }

    suspend fun translateSysGrp() {
        val patchDir = translationDir["images/sysgrp"].jail()

        println("Translating sysgrp.arc...")
        repackArc(gameDir["sysgrp.arc"]) { name, data ->
            translateImage(patchDir, name) { data } ?: data
        }
    }

    suspend fun translateGraphic() {
        for (arcName in listOf("data02000.arc", "data02001.arc")) {
            println("Translating $arcName...")
            val patchDir = translationDir["images/graphic"].jail()
            val outputDir = gameDir["Graphic/CVTD"].apply { mkdirs() }.jail()
            val arc = gameDir[arcName].openAsArc()
            for (file in arc) {
                val out = translateImage(patchDir, file.basename) { file.readBytes() }
                if (out != null) {
                    outputDir[file.basename].writeBytes(out)
                }
            }
        }
    }

    suspend fun translateScript() {
        println("Translating script (data01000.arc)...")
        val outputDir = gameDir["Script/CVTD"].apply { mkdirs() }.jail()
        val arc = gameDir["data01000.arc"].openAsArc()
        for (file in arc) {
            print(" - Translating ${file.basename}...")
            val scriptBytes = DSC.decompressIfRequired(file.readAll())
            val scriptOps = BSS.load(scriptBytes)
            val po = PO.load(translationDir["${file.basename}.po"].readString().lines())
            val acme = po.toAcme()
            val translatedOps = BssTranslation.translate2(scriptOps) { id, full, title, body ->
                acme[id]?.text?.replace('“', '"')?.replace('”', '"') ?: full
            }
            //val translatedOps = scriptOps
            val translatedScriptBytes = BSS.save(translatedOps)
            outputDir[file.basename].writeBytes(translatedScriptBytes)
            println("Ok")
        }
    }
}