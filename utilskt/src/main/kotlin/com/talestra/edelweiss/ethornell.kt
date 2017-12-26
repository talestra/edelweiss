package com.talestra.edelweiss

import com.soywiz.korim.color.RGB
import com.soywiz.korim.color.RGBA
import com.soywiz.korim.format.TGA
import com.soywiz.korim.format.defaultImageFormats
import com.soywiz.korim.format.registerStandard
import com.soywiz.korim.format.writeTo
import com.soywiz.korio.Korio
import com.soywiz.korio.error.ignoreErrors
import com.soywiz.korio.lang.ASCII
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.uniVfs
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess

// This program is released AS IT IS. Without any warranty and responsibility from the author.

fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell") + args) }

object Example {
    @JvmStatic
    fun main(args: Array<String>) = Korio { ARC.build("/Users/soywiz/projects/edelweiss/game/system.arc.d", level = 9) }
}

object Example2 {
    init {
        defaultImageFormats.registerStandard()
    }

    @JvmStatic
    fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell", "-x9", "/Users/soywiz/projects/edelweiss/game/data06000.arc")) }
    //fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell", "-x9", "/Users/soywiz/projects/edelweiss/game/data02000.arc")) }
    //fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell", "-x9", "/Users/soywiz/projects/edelweiss/game/system.arc")) }
    //fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell", "-x0", "/Users/soywiz/projects/edelweiss/game/data02000.arc", "01_dou_tuu_l")) }
    //fun main(args: Array<String>) = Korio { main_s(arrayOf("ethornell", "-x9", "/Users/soywiz/projects/edelweiss/game/data02000.arc", "01_dou_tuu_l")) }
}

// Version of the utility.
const private val _version = "0.3"

// Utility functin for the decrypting.
fun hash_update(hash_val: IntRef): Int {
    var eax: Int
    var ebx: Int
    var edx: Int
    var esi: Int
    var edi: Int
    //writefln("V:%08X", hash_val);
    // @TODO: Check if this multiplication is unsigned?
    edx = (20021 * LOWORD(hash_val.v))
    eax = (20021 * HIWORD(hash_val.v)) + (346 * hash_val.v) + HIWORD(edx)
    hash_val.v = (LOWORD(eax) shl 16) + LOWORD(edx) + 1
    //writefln("D:%08X", edx);
    //writefln("A:%08X", eax);
    return eax and 0x7FFF
}

//fun RGBA.avg(vararg colors: Int): Int = RGBA.pack(
//        colors.sumBy { RGBA.getR(it) } / colors.size,
//        colors.sumBy { RGBA.getG(it) } / colors.size,
//        colors.sumBy { RGBA.getB(it) } / colors.size,
//        colors.sumBy { RGBA.getA(it) } / colors.size
//)

//class Color(var v: Int = 0) {
//    var r: Int get() = RGBA.getFastR(v); set(r) = run { v = RGBA.pack(r, g, b, a) }
//    var g: Int get() = RGBA.getFastG(v); set(g) = run { v = RGBA.pack(r, g, b, a) }
//    var b: Int get() = RGBA.getFastB(v); set(b) = run { v = RGBA.pack(r, g, b, a) }
//    var a: Int get() = RGBA.getFastA(v); set(a) = run { v = RGBA.pack(r, g, b, a) }
//
//    constructor(r: Int, g: Int, b: Int, a: Int) : this(RGBA.pack(r, g, b, a))
//    constructor(r: Byte, g: Byte, b: Byte, a: Byte) : this(RGBA.pack(r.toUnsigned(), g.toUnsigned(), b.toUnsigned(), a.toUnsigned()))
//
//    inner class VV {
//        operator fun get(n: Int) = (v ushr (8 * n)) and 0xFF
//        operator fun set(n: Int, value: Int) = run { v = (v and (0xFF shl (8 * n)).inv()) or (value and 0xFF) shl (8 * n) }
//    }
//
//    val vv = VV()
//
//    companion object {
//        fun avg(vararg v: Color): Color {
//            val c = Color()
//            val vv = IntArray(4)
//            for (n in 0 until v.size) for (m in 0 until 4) vv[m] += v[n].vv[m]
//            for (m in 0 until 4) c.vv[m] = vv[m] / v.size
//            return c
//        }
//    }
//
//    operator fun plus(a: Color): Color {
//        val c = Color(0)
//        for (n in 0 until 4) c.vv[n] = this.vv[n] + a.vv[n]
//        return c
//    }
//
//    override fun toString(): String = format("#%02X%02X%02X%02X", r, g, b, a)
//}
//static assert(Color.sizeof  == 4, "Invalid size for Color");


class BitWritter(private val data: ByteArrayBuilder = ByteArrayBuilder()) {
    var cval: Long = 0L
    var av_bits: Int = 8

    companion object {
        fun mask(bits: Int): Long = (1L shl bits) - 1

        fun reverse(b: Int): Int {
            var b = b
            b = b and 0xF0 shr 4 or (b and 0x0F shl 4)
            b = b and 0xCC shr 2 or (b and 0x33 shl 2)
            b = b and 0xAA shr 1 or (b and 0x55 shl 1)
            return b
        }
    }

    //fun putbit (bit: Boolean) {
    //    if (bit) {
    //        cval = cval or (1 shl --av_bits).toLong()
    //    } else {
    //        --av_bits
    //    }
    //    if (av_bits == 0) flush()
    //}
//
    //fun write (ins_val: Long, ins_bits: Int) {
    //    for (n in 0 until ins_bits) {
    //        val bit = ((ins_val ushr (ins_bits - n - 1)) and 1) != 0L
    //        putbit(bit)
    //    }
    //}

    fun write(ins_val: Long, ins_bits: Int) {
        var ins_val: Long = ins_val
        var ins_bits: Int = ins_bits

        //writefln("%s", varbits(ins_val, ins_bits));
        val ins_bits0 = ins_bits

        while (ins_bits > 0) {
            val bits = min(ins_bits, av_bits)
            val extract = (ins_val ushr (ins_bits0 - bits)) and mask(bits)
            //writefln("  %s", varbits(extract, bits));

            cval = cval or (extract shl (av_bits - bits))

            ins_val = ins_val shl bits
            ins_bits -= bits
            av_bits -= bits
            if (av_bits <= 0) flush()
        }
    }

    fun flush() {
        if (av_bits == 8) return
        //writefln("  byte: %08b", cval);
        data.append(cval.toByte())
        av_bits = 8
        cval = 0
        //exit(0);
    }

    fun finish(): ByteArray {
        flush()
        return data.toByteArray()
    }
}


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

suspend fun main_s(args: Array<String>) {
    //fun main_s(args: Array<String>) {
    // Shows the help for the usage of the program.
    fun show_help() {
        writefln("Ethornell utility %s - soywiz - 2009 - Build %s", _version, __TIMESTAMP__)
        writefln("Knows to work with English Shuffle! and Edelweiss with Ethornell 1.69.140")
        writefln()
        writefln("ethornell <command> <parameters>")
        writefln()
        writefln("  -l       List the contents of an arc pack")
        writefln("  -x[0-9]  Extracts the contents of an arc pack (uncompressing when l>0)")
        writefln("  -p[0-9]  Packs and compress a folder")
        writefln()
        writefln("  -d       Decompress a single file")
        writefln("  -c[0-9]  Compress a single file")
        writefln("  -t[0-9]  Test the compression")
        writefln()
        writefln("  -h       Show this help")
    }

    // Throws an exception if there are less parameters than the required.
    fun expect_params(count: Int) {
        if (args.size < (count + 2)) throw(ShowHelpException(format("Expected '%d' params and '%d' received", count, args.size - 2)))
    }

    try {
        if (args.size < 2) throw ShowHelpException()

        var params = arrayListOf<String>()
        if (args.size > 2) params = ArrayList(args.copyOfRange(2, args.size).toList())


        fun check_image(i: ImageHeader): Boolean =
                ((i.bpp % 8) == 0) && (i.bpp > 0) && (i.bpp <= 32) &&
                        (i.width > 0) && (i.height > 0) &&
                        (i.width < 8096) && (i.height < 8096) &&
                        (i.zpad0 == 0) && (i.zpad1 == 0)

        fun write_image(ih: ImageHeader, out_file: String, data: ByteArray) {
            //val f = BufferedFile(out_file, FileMode.OutNew);
            val bmp = when (ih.bpp) {
                32 -> RGBA.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data)
                24 -> RGB.decodeToBitmap32(ih.width.toInt(), ih.height.toInt(), data)
                else -> throw(Exception("Unknown bpp"))
            }

            std_file_write(out_file, TGA.encode(bmp))
            //f.close();
        }

        when (args[1][0 until 2]) {
        // List.
            "-l" -> {
                expect_params(1)
                val arc_name = params[0]

                // Check if the arc actually exists.
                assert(std_file_exists(arc_name)) { format("File '%s' doesn't exists", arc_name) }

                // Writes a header with the arc file that we are processing.
                writefln("----------------------------------------------------------------")
                writefln("ARC: %s", arc_name)
                writefln("----------------------------------------------------------------")
                // Iterate over the ARC file and write the files.
                for (e in ARC(arc_name)) println(e.name)
            }
        // Extact + uncompress.
            "-x" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val arc_name = params[0]

                // Check if the arc actually exists.
                assert(File(arc_name).exists()) { format("File '%s' doesn't exists", arc_name) }

                // Determine the output path and create the folder if it doesn't exists already.
                val out_path = arc_name + ".d"
                ignoreErrors { File(out_path).mkdirs() }

                // Iterate over the arc file.
                for (e in ARC(arc_name)) {
                    if (params.length >= 2) {
                        var found = false
                        for (filter in params[1 until params.length]) {
                            if (filter == e.name) {
                                found = true; break; }
                        }
                        if (!found) continue
                    }
                    val s = e.open()
                    writef("%s...", e.name)
                    var out_file: String = ""
                    if (params.length >= 2) {
                        out_file = e.name
                    } else {
                        out_file = "$out_path/${e.name}"
                    }

                    try {
                        var result = "Ok"
                        // Check the first 0x10 bytes to determine the magic of the file.
                        when (s.sliceWithStart(0L).readString(0x10, ASCII)) {
                        // Encrypted+Static Huffman+LZ
                            "DSC FORMAT 1.00\u0000" -> {
                                writef("DSC...")
                                if (File(out_file).exists()) {
                                    result = "Exists"
                                } else {
                                    var data = byteArrayOf()
                                    if (level == 0) {
                                        data = s.readBytes(s.length.toInt())
                                    } else {
                                        data = DSC.decompress(s.readAll())
                                    }
                                    val ih: ImageHeader = ImageHeader.read(data.openSync())
                                    if (check_image(ih)) {
                                        writef("Image...BPP(%d)...", ih.bpp)
                                        out_file += ".tga"
                                        if (std_file_exists(out_file)) throw Exception("Exists")
                                        write_image(ih, out_file, data.copyOfRange(0x10, data.size))
                                    } else {
                                        std_file_write(out_file, data)
                                    }
                                }
                            }
                        // Encrypted+Dynamic Huffman+RLE+LZ+Unpacking+Row processing
                            "CompressedBG___\u0000" -> {
                                val out_file = out_file
                                val out_png_file = "$out_file.png"

                                writef("CBG...")
                                if (level == 0) {
                                    if (std_file_exists(out_file)) {
                                        println("Exists")
                                    } else {
                                        std_file_write(out_file, s.readAll())
                                    }
                                } else {
                                    if (std_file_exists(out_png_file)) {
                                        println("Exists")
                                    } else {
                                        CompressedBg.load(s.readAll()).writeTo(out_png_file.uniVfs)
                                        println("Ok")
                                    }
                                }
                            }
                        // Uncompressed/Unknown.
                            else -> {
                                val ss = s.sliceWithStart(6L)
                                val ih = ImageHeader.read(ss)
                                if (check_image(ih)) {
                                    writef("Image...BPP(%d)...", ih.bpp)
                                    out_file += ".tga"
                                    if (std_file_exists(out_file)) throw Exception("Exists")
                                    s.position = 0x10
                                    write_image(ih, out_file, s.readBytes((s.length - s.position).toInt()))
                                } else {
                                    writef("Uncompressed...")
                                    if (std_file_exists(out_file)) throw Exception("Exists")
                                    File(out_file).writeBytes(s.readAll())
                                }
                            }
                        }
                        println(result)
                    }
                    // There was an error, write it.
                    catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        // Packs and compress a folder.
            "-p" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val folder_in = params[0]
                ARC.build(folder_in, level)
            }
        // Decompress a single file.
            "-d" -> {
                expect_params(1)
                val file_name = params[0]
                val out_file = file_name + ".u"

                // Check if the file actually exists.
                assert(std_file_exists(file_name)) { format("File '%s' doesn't exists", file_name) }

                run {
                    val s = File(file_name).readBytes().openSync()
                    when (s.slice().readString(0x10, ASCII)) {
                    // Encrypted+Dynamic Huffman+RLE+LZ+Unpacking+Row processing
                        "CompressedBG___\u0000" -> {
                            CompressedBg.load(s.readAll()).writeTo(out_file.uniVfs)
                        }
                        else -> {
                            val uncompressed = DSC.decompress(File(file_name).readBytes())
                            File(out_file).writeBytes(uncompressed)
                        }
                    }
                }
            }
        // Compress a single file.
            "-c" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val file_name = params[0]
                val out_file = file_name + ".c"

                // Check if the file actually exists.
                assert(File(file_name).exists()) { format("File '%s' doesn't exists", file_name) }

                File(out_file).writeBytes(DSC.compress(std_file_read(file_name), level))
            }
        // Test the compression.
            "-t" -> {
                var level = 9
                if (args[1].length == 3) level = args[1][2] - '0'
                expect_params(1)
                val file_name = params[0]

                // Check if the file actually exists.
                assert(File(file_name).exists()) { format("File '%s' doesn't exists", file_name) }

                val uncompressed0 = std_file_read(file_name)
                val compressed = DSC.compress(uncompressed0, level)
                val uncompressed1 = DSC.decompress(compressed)

                assert(uncompressed0.contentEquals(uncompressed1)) { "Failed" }
                writefln("Ok")
            }
        // Help command.
            "-h" -> throw ShowHelpException()
        // Unknown command.
            else -> {
                throw ShowHelpException(format("Unknown command '%s'", args[1]))
            }
        }

        exitProcess(0)
    }
    // Catch a exception to show the help/usage.
    catch (e: ShowHelpException) {
        show_help()
        e.printStackTrace()
        exitProcess(0)
    }
    // Catch a generic unhandled exception.
    catch (e: Throwable) {
        e.printStackTrace()
        exitProcess(-1)
    }
}
