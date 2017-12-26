package com.talestra.edelweiss

import com.soywiz.korio.error.ignoreErrors
import com.soywiz.korio.util.substr
import java.io.File
import kotlin.system.exitProcess

// Version of the utility.
const private val _version = "0.3"


private fun patch(game_folder: String, acme_folder: String) {
    val script_folder_in = "$game_folder/data01000.arc.d"
    val script_folder_out = "$game_folder/Script/CVTD"

    val acme = ACME()

    println("Patch all:")
    println(" - script_folder_in : $script_folder_in")
    println(" - script_folder_out: $script_folder_out")
    println(" - acme_folder      : $acme_folder")

    ignoreErrors { File(game_folder + "/Script").mkdirs() }
    ignoreErrors { File(game_folder + "/Script/CVTD").mkdirs(); }

    for (file in listdir(script_folder_in)) {
        println(file)
        val file_in = "$script_folder_in/$file"
        val file_out = "$script_folder_out/$file"
        val acme_in = "$acme_folder/$file.txt"

        println("  ACME parsing...")
        acme.parseForm2(acme_in)
        println("  BSS parsing...")
        val ops = BSS.load(file_in)
        println("  BSS patching...")
        val pops = BssTranslation.patchStrings(ops, acme)
        writefln("  BSS writting...")
        File(file_out).writeBytes(BSS.save(pops))
        //bss.dump();
    }
}

fun extract_all2(game_folder: String, acme_folder: String) {
    val script_folder_in = game_folder + "/data01000.arc.d"

    println("Extract all:")
    println(" - script_folder: $script_folder_in")
    println(" - acme_folder  : $acme_folder")

    val file_list = File(script_folder_in).list().sorted()
    if (file_list.isNotEmpty()) {
        for (file in file_list) {
            if (file[0] == '.') continue
            println(file)
            val ops = BSS.load(script_folder_in + "/" + file)
            val acme = BssTranslation.extract(ops)
            acme.writeForm2("$acme_folder/$file.txt", file)
        }
    } else {
        println("No files detected.")
    }
}

fun main(args2: Array<String>) {
    val args = arrayOf("script.exe") + args2
    fun show_help() {
        println("Ethornell script utility $_version - soywiz - 2009 - Build $__TIMESTAMP__")
        println("Knows to work with English Shuffle! and Edelweiss with Ethornell 1.69.140")
        println()
        println("script <command> <game_folder> <text_folder>")
        println()
        println("  -x[1,3]  Extracts texts from a folder with scripts.")
        println("  -p       Patches a folder with scripts with modified texts.")
        println()
        println("  -h       Show this help")
    }

    try {
        if (args.size < 2) throw ShowHelpException()

        when (args[1].substr(0, 2)) {
            "-x" -> extract_all2(args[2], args[3]) // Game folder -> Text folder
            "-p" -> patch(args[2], args[3]) // Game folder <- Text folder
        // Unknown command.
            else -> throw ShowHelpException("Unknown command '${args[1]}'")
        }
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

    exitProcess(0)
}

