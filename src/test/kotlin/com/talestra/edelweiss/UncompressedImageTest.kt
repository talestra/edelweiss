package com.talestra.edelweiss

import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.bitmap.computePsnr
import com.soywiz.korim.format.PNG
import com.soywiz.korio.Korio
import com.soywiz.korio.vfs.resourcesVfs
import org.junit.Assert
import org.junit.Test

class UncompressedImageTest {
    @Test
    fun name() = Korio {
        val expected = PNG.decode(resourcesVfs["SGCG000000.png"].readBytes()).toBMP32()
        val image = UncompressedImage.load(resourcesVfs["SGCG000000"].readBytes()).toBMP32()

        Assert.assertTrue(Bitmap32.computePsnr(expected, image) == Double.POSITIVE_INFINITY)
    }
}