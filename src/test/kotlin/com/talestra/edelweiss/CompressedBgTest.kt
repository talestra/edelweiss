package com.talestra.edelweiss

import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.bitmap.computePsnr
import com.soywiz.korim.format.defaultImageFormats
import com.soywiz.korim.format.readBitmapNoNative
import com.soywiz.korim.format.registerStandard
import com.soywiz.korio.async.syncTest
import com.soywiz.korio.vfs.resourcesVfs
import org.junit.Assert
import org.junit.Test

class CompressedBgTest {
    @Test
    fun name() = syncTest {
        defaultImageFormats.registerStandard()
        val bmp = CompressedBg.load(resourcesVfs["01_dou_tuu_l"].readAll())
        val expected = resourcesVfs["01_dou_tuu_l.png"].readBitmapNoNative().toBMP32()
        Assert.assertEquals(Bitmap32.computePsnr(bmp, expected), Double.POSITIVE_INFINITY, 0.0)
    }
}