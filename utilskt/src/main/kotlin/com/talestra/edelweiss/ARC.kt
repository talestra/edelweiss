package com.talestra.edelweiss

import com.soywiz.korio.lang.ASCII
import com.soywiz.korio.lang.LATIN1
import com.soywiz.korio.lang.format
import com.soywiz.korio.lang.toString
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.LocalVfs
import com.soywiz.korio.vfs.VfsFile
import com.soywiz.korio.vfs.VfsOpenMode
import java.io.File


// Class to have read access to ARC files.
class ARC : Iterable<ARC.Entry> {
    val s: SyncStream
    val sd: SyncStream
    val table = arrayListOf<Entry>()
    val table_lookup = LinkedHashMap<String, Entry>()

    // Entry for the header.
    class Entry(
            val nameBytes: ByteArray = ByteArray(0x10), // Stringz with the name of the file.
            val start: Int = 0, // Slice of the file.
            val len: Int = 0
    ) {
        lateinit var arc: ARC

        companion object {
            fun read(s: SyncStream): Entry = s.run {
                Entry(
                        readBytes(0x10),
                        readS32_le(),
                        readS32_le()
                ).apply {
                    readS32_le() // pad
                    readS32_le() // pad
                }
            }
        }


        // Obtaining the processed name as a String.
        val name: String get() = nameBytes.openSync().readStringz()

        override fun toString(): String = "%-16s (%08X-%08X)".format(name, start, len)

        // Open a read-only stream for the file.
        fun open(): SyncStream = arc.open(this)

        // Method to save this entry to a file.
        fun save(name: String = this.name) {
            val s = MemorySyncStream()
            open().copyTo(s)
            s.close()
            File(name).writeBytes(s.toByteArray())
        }

        // Defines the explicit cast to Stream.
        //Stream opCast() { return open; }
    }

    // Check the struct to have the expected size.
    //static assert(Entry.sizeof == 0x20, "Invalid size for ARC.Entry");

    // Open a ARC using an stream.
    constructor(s: SyncStream, name: String = "unknwon") {
        this.s = s

        // Check the magic.
        assert(s.readString(12, LATIN1) == "PackFile    ") { format("It doesn't seems to be an ARC file ('%s')", name) }

        // Read the size.
        val table_length = s.readS32_le()

        table += (0 until table_length).map { Entry.read(s) }

        // Stre a SliceStream starting with the data part.
        sd = s.sliceWithStart(s.position)

        // Iterates over all the entries, creating references to this class, and creating a lookup table.
        for (n in 0 until table.length) {
            table_lookup[table[n].name] = table[n]
            table[n].arc = this
        }
    }

    // Open an ARC using a file name.
    constructor(name: String) : this(File(name).readBytes().openSync(), name)

    // Gets a read-only stream for a entry.
    fun open(e: Entry): SyncStream = sd.sliceWithBounds(e.start.toLong(), (e.start + e.len).toLong())

    // Defines an iterator for this class.
    override fun iterator(): Iterator<Entry> = table.iterator()

    // Defines an array accessor to obtain an entry file.
    operator fun get(name: String): Entry = table_lookup[name] ?: throw Exception(format("Unknown index '%s'", name))

    companion object {
        suspend fun build(folder_in: String, level: Int) {
            val arc_out = folder_in[0 until folder_in.length - 2]

            val files = listdir(folder_in)
            //val arcs = LocalVfs(arc_out).open(VfsOpenMode.CREATE_OR_TRUNCATE)
            val folder = LocalVfs(folder_in)

            assert(std_file_exists(folder_in)) { format("Folder '%s' doesn't exists", folder_in) }
            assert(folder_in[folder_in.length - 6 until folder_in.length] == ".arc.d") { format("Folder '%s', should finish by .arc.d", folder_in) }

            if (File(arc_out).exists() && !File("$arc_out.bak").exists()) {
                File(arc_out).copyTo(File("$arc_out.bak"))
            }

            ARC.build(LocalVfs(arc_out), files, level) { file -> folder[file].readAll() }
        }

        suspend fun build(out: VfsFile, files: List<String>, level: Int, reader: suspend (String) -> ByteArray) {
            val s = out.open(VfsOpenMode.CREATE_OR_TRUNCATE)
            try {
                // Check if the file actually exists.
                val count = files.size
                s.writeString("PackFile    ")
                s.write32_le(count.toInt())
                var pos = 0

                for ((k, file_name) in files.withIndex()) {
                    writef("%s...", file_name)
                    val data: ByteArray = reader(file_name)
                    val cdata: ByteArray
                    // Already compressed.
                    if (data.sliceArray(0 until 0x10).toString(ASCII) == "DSC FORMAT 1.00\u0000") {
                        cdata = data
                        writefln("Already compressed")
                    }
                    // Not compressed.
                    else {
                        cdata = DSC.compress(data, level)
                        writefln("Compressed")
                    }
                    s.position = (0x10 + count * 0x20 + pos).toLong()
                    s.writeBytes(cdata)
                    s.position = (0x10 + k * 0x20).toLong()
                    s.writeString(file_name)
                    while ((s.position % 0x10L) != 0L) s.write8(0)
                    s.write32_le(pos)
                    s.write32_le(cdata.size)
                    s.write32_le(0)
                    s.write32_le(0)
                    pos += cdata.size
                }
            } finally {
                s.close()
            }
        }
    }
}
