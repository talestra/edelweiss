package com.talestra.edelweiss.scripts

import com.soywiz.korio.Korio
import com.soywiz.korio.vfs.uniVfs
import com.talestra.edelweiss.BSS
import com.talestra.edelweiss.BssTranslation
import com.talestra.edelweiss.uncompressDsc
import com.talestra.edelweiss.openAsArc

object GeneratePoFiles {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val arc = "game".uniVfs["data01000.arc"].openAsArc()
        val translationDir = "translation".uniVfs
        for (file in arc) {
            val scriptBytes = file.readBytes().uncompressDsc()
            val scriptOps = BSS.load(scriptBytes)
            val acme = BssTranslation.extract(scriptOps)
            translationDir["${file.basename}.po"].writeBytes(acme.generatePo(file.basename))
        }
        //println("GeneratePoFiles!")

    }
}
