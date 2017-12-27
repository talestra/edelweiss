package com.talestra.edelweiss

import com.soywiz.korio.util.quoted
import org.junit.Assert
import org.junit.Test

class POTest {
    @Test
    fun name() {
        val res = """
        # Comments for '007a6'
        msgid ""
        msgstr ""
        "Project-Id-Version: \n"
        "POT-Creation-Date: \n"
        "PO-Revision-Date: \n"
        "Last-Translator: \n"
        "Language-Team: \n"
        "MIME-Version: 1.0 \n"
        "Content-Type: text/plain; charset=utf-8 \n"
        "Content-Transfer-Encoding: 8bit \n"
        "Language: es\n"

        #: 007a6:8

        msgid "If I was with Haruka, I bet the time would fly right by."
        msgstr ""

        #: 007a6:9

        msgid "{Kazushi}\n\"I wonder where she is?\""
        msgstr ""
        """
        val po = PO.load(res.lines())
        Assert.assertEquals("[[], [007a6:8], [007a6:9]]", po.entries.map { it.references }.toString())
        Assert.assertEquals("""["", "If I was with Haruka, I bet the time would fly right by.", "{Kazushi}\n\"I wonder where she is?\""]""", po.entries.map { it.msgid.quoted }.toString())
        Assert.assertEquals("""["Project-Id-Version: \nPOT-Creation-Date: \nPO-Revision-Date: \nLast-Translator: \nLanguage-Team: \nMIME-Version: 1.0 \nContent-Type: text/plain; charset=utf-8 \nContent-Transfer-Encoding: 8bit \nLanguage: es\n", "", ""]""", po.entries.map { it.msgstr.quoted }.toString())
        println(po.save().joinToString("\n"))
    }
}