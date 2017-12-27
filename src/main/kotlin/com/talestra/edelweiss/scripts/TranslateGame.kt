package com.talestra.edelweiss.scripts

import com.soywiz.korim.format.defaultImageFormats
import com.soywiz.korim.format.readBitmapNoNative
import com.soywiz.korim.format.registerStandard
import com.soywiz.korio.Korio
import com.soywiz.korio.async.toList
import com.soywiz.korio.vfs.VfsFile
import com.soywiz.korio.vfs.localCurrentDirVfs
import com.talestra.edelweiss.ARC
import com.talestra.edelweiss.EdelweissImage
import com.talestra.edelweiss.openAsArc

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
                println("Patched $name")
                EdelweissImage.save(replaceImageFile.readBitmapNoNative())
            }
            patchImageFile.exists() -> {
                val image = EdelweissImage.load(data()).toBMP32()
                image.draw(patchImageFile.readBitmapNoNative().toBMP32(), 0, 0)
                println("Patched $name")
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
        println("Translating data02001.arc...")
        val patchDir = translationDir["images/graphic"].jail()
        val outputDir = gameDir["Graphic/CVTD"].ensureParents().jail()
        val arc = gameDir["data02001.arc"].openAsArc()
        for (file in arc) {
            val out = translateImage(patchDir, file.basename) { file.readBytes() }
            if (out != null) {
                println(file.basename)
                outputDir[file.basename].writeBytes(out)
            }
        }
    }
}