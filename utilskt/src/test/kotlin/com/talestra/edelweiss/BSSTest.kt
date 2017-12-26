package com.talestra.edelweiss

import com.soywiz.korio.async.syncTest
import com.soywiz.korio.stream.openSync
import com.soywiz.korio.vfs.resourcesVfs
import org.junit.Test

class BSSTest {
    @Test
    fun name() = syncTest {
        val bss = BSS().apply {
            parse(resourcesVfs["007a6.u"].readAll().openSync())
        }
        //for (op in bss.ops) println(op)
    }
}

/*

unittest {
    char[] c;
    c = "This is\n a test.\t\t\\\r\\\\\\.";
    assert(stripslashes(addslashes(c)) == c);
}

*/
