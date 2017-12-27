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

    suspend fun translateSysGrp() {
        val patchDir = translationDir["images/sysgrp"].jail()

        println("Translating sysgrp.arc...")
        repackArc(gameDir["sysgrp.arc"]) { name, data ->
            //println(patchDir.absolutePath)

            val replaceImageFile = patchDir["$name.png"]
            val patchImageFile = patchDir["$name.patch.png"]

            when {
                replaceImageFile.exists() -> {
                    println("Patched $name")
                    EdelweissImage.save(replaceImageFile.readBitmapNoNative())
                }
                patchImageFile.exists() -> {
                    val image = EdelweissImage.load(data).toBMP32()
                    image.draw(patchImageFile.readBitmapNoNative().toBMP32(), 0, 0)
                    println("Patched $name")
                    EdelweissImage.save(image)
                }
                else -> data
            }
        }
    }
}