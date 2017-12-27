package com.talestra.edelweiss

import com.soywiz.korio.async.syncTest
import com.soywiz.korio.stream.openSync
import com.soywiz.korio.vfs.resourcesVfs
import org.junit.Assert
import org.junit.Test

class BSSTest {
    @Test
    fun name() = syncTest {
        val input = resourcesVfs["007a6.u"].readAll()
        val ops = BSS.load(input.openSync())
        val generated = BSS.save(ops)
        //for (op in ops) println(op)
        //val acme = BSS.extract(ops)
        //println(acme)
        //localCurrentDirVfs["007a6.u.patched"].writeBytes(BSS.serialize(ops))
        Assert.assertArrayEquals(input, generated)
    }
}

/*

unittest {
    char[] c;
    c = "This is\n a test.\t\t\\\r\\\\\\.";
    assert(stripslashes(addslashes(c)) == c);
}

*/
