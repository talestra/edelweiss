package com.talestra.edelweiss

import com.soywiz.korim.bitmap.Bitmap
import com.soywiz.korio.error.invalidOp

object EdelweissImage {
    fun load(data: ByteArray): Bitmap {
        if (DSC.isCompressed(data)) return load(data.uncompressDsc())
        if (CompressedBg.detect(data)) return CompressedBg.load(data)
        if (UncompressedImage.check(data)) return UncompressedImage.load(data)
        invalidOp("Not a supported image!")
    }

    fun save(data: Bitmap, level: Int = 0): ByteArray {
        return UncompressedImage.save(data.toBMP32())
    }
}