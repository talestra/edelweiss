package com.talestra.edelweiss.scripts

import com.soywiz.korio.Korio
import com.soywiz.korio.vfs.localCurrentDirVfs
import com.soywiz.korio.vfs.uniVfs
import com.talestra.edelweiss.BSS
import com.talestra.edelweiss.BssTranslation
import com.talestra.edelweiss.openAsArc
import com.talestra.edelweiss.uncompressDsc

object GeneratePoFiles {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val gameDir = localCurrentDirVfs["game"]
        val arc = gameDir["data01000.arc"].openAsArc()
        val translationDir = "translation".uniVfs
        for (file in arc) {
            val outFile = translationDir["${file.basename}.po"]
            print("${outFile.basename}...")
            if (outFile.exists()) {
                println("Exists")
            } else {
                val scriptBytes = file.readBytes().uncompressDsc()
                val scriptOps = BSS.load(scriptBytes)
                val acme = BssTranslation.extract(scriptOps)
                outFile.writeBytes(acme.generatePo(file.basename))
                println("Ok")
            }
        }
        //println("GeneratePoFiles!")
    }
}
