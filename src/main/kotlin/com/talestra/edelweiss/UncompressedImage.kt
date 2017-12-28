package com.talestra.edelweiss

import com.soywiz.korim.bitmap.Bitmap32
import com.soywiz.korim.color.BGRA
import com.soywiz.korim.color.ColorFormat24
import com.soywiz.korim.format.ImageEncodingProps
import com.soywiz.korim.format.PNG
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.extract8
import com.soywiz.korio.util.insert8

object UncompressedImage {
    class ImageHeader(
            var width: Short = 0,
            var height: Short = 0,
            var bpp: Int = 0,
            var zpad0: Int = 0,
            var zpad1: Int = 0
    ) {
        companion object {
            fun read(s: SyncStream): ImageHeader = s.run {
                ImageHeader(
                        width = readS16_le().toShort(),
                        height = readS16_le().toShort(),
                        bpp = readS32_le(),
                        zpad0 = readS32_le(),
                        zpad1 = readS32_le()
                )
            }
        }
    }

    fun check(data: ByteArray): Boolean = check(ImageHeader.read(data.openSync()))

    fun check(i: ImageHeader): Boolean =
            ((i.bpp % 8) == 0) && (i.bpp > 0) && (i.bpp <= 32) &&
                    (i.width > 0) && (i.height > 0) &&
                    (i.width < 8096) && (i.height < 8096) &&
                    (i.zpad0 == 0) && (i.zpad1 == 0)

    @Deprecated("Use load instead")
    fun write_image(ih: ImageHeader, out_file: String, data: ByteArray) {
        //val f = BufferedFile(out_file, FileMode.OutNew);
        val bmp = when (ih.bpp) {
            32 -> BGRA.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data)
            24 -> BGR.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data)
            else -> throw(Exception("Unknown bpp"))
        }

        std_file_write(out_file, PNG.encode(bmp, ImageEncodingProps(quality = 0.1)))
        //f.close();
    }

    fun load(data: ByteArray): Bitmap32 {
        val ih = ImageHeader.read(data.openSync())
        //val f = BufferedFile(out_file, FileMode.OutNew);
        val bmp = when (ih.bpp) {
            32 -> BGRA.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data, 0x10)
            24 -> BGR.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data, 0x10)
            else -> throw(Exception("Unknown bpp ${ih.bpp}"))
        }
        return bmp
    }

    fun save(bmp: Bitmap32): ByteArray {
        return MemorySyncStreamToByteArray {
            write16_le(bmp.width)
            write16_le(bmp.height)
            write32_le(32)
            write32_le(0)
            write32_le(0)
            writeBytes(BGRA.encode(bmp.data))
        }
    }
}

// @TODO: Move to Korim
object BGR : ColorFormat24() {
    override fun getR(v: Int): Int = v.extract8(16)
    override fun getG(v: Int): Int = v.extract8(8)
    override fun getB(v: Int): Int = v.extract8(0)
    override fun getA(v: Int): Int = 0xFF

    override fun pack(r: Int, g: Int, b: Int, a: Int): Int = 0.insert8(b, 0).insert8(g, 8).insert8(r, 16)
}