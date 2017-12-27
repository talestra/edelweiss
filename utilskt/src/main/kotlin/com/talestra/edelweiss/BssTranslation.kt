package com.talestra.edelweiss


object BssTranslation {
    class BssStack {
        val args: ArrayList<Any?> = arrayListOf()
        fun push(v: Any?) = run { args.add(v) }
        fun int(index: Int) = args[index] as Int
        fun str(index: Int) = args[index] as? String?
        fun pop() = args.removeAt(args.size - 1)
        fun popi() = pop() as Int
        fun clear() = run { args.clear() }
    }

    fun patchStrings(ops: List<BSS.OP>, acme: ACME): List<BSS.OP> {
        var ops = ops

        data class PATCH(
                var pos: Int = 0,
                val ops: ArrayList<BSS.OP> = arrayListOf()
        )

        val patches = arrayListOf<PATCH>()
        var line = 0
        var line_pos = 0
        val pushes = arrayListOf<BSS.OP>()
        var sstack = BssStack()
        var font_width = 22
        var font_height = 22
        var last_op_type: Int = 0
        var changed_size = false
        for ((pos, op) in ops.withIndex()) {
            when (op.type) {
                0x7F -> { // SCRIPT_LINE
                    changed_size = (last_op_type == 0x1_4D)
                    line = op.int(1)
                    line_pos = pos + 1
                    sstack.clear()
                    pushes.clear()
                }
                BSS.Opcodes.PUSH_INT -> run { sstack.push(op.int(0)); pushes += op }
                BSS.Opcodes.PUSH_STR -> run { sstack.push(op.str(0)); pushes += op }
                BSS.Opcodes.STACK -> {
                    //writefln(op);
                }
                BSS.Opcodes.TEXT_PUT -> { // TEXT_WRITE
                    //writefln("TEXT_WRITE");
                    if (acme.has(line)) {
                        var text = acme[line]!!.text

                        if (sstack.str(1) != null) {
                            sstack.args[1] = sstack.str(1)
                        }
                        //writefln("::%s::%s::", sstack.s[1], text);

                        // Has title.
                        if ((sstack.str(1) != null) && sstack.str(1)!!.trim().length != 0) {
                            val tt = explode("\n", text, 2)
                            var title = tt[0].trim()
                            text = if (tt.length >= 2) tt[1] else ""
                            assert(title.length > 2) { format("ID/Line(@%d): Title length > 2", line) }
                            assert(title[0] == '{') { format("ID/Line(@%d): Line doesn't start by '{'", line) }
                            assert(title[title.length - 1] == '}') { format("ID/Line(@%d): Line end by '}'", line) }
                            title = title[1 until title.length - 1]
                            //while (title.length < 5) title += " ";
                            // Ignore current title, and use the original one.
                            // Another title won't work on Edelweiss.
                            title = sstack.str(1)!!
                            //writefln(pushes[1]);
                            pushes[1].args[0] = title
                        }
                        // Has text.
                        if (sstack.args[0] != null) {
                            var ttext = text.trimEnd()
                            //writefln(pushes[0]);
                            ttext = ttext.replace("\r", "").replace("\n ", " ").replace(" \n", " ").replace("\n", " ")
                            pushes[0].args[0] = ttext
                            //pushes[0].s[0] = ttext;

                            val calc_lines = (ttext.length / 42) + 1

                            if ((font_height <= 22) && (font_height >= 19)) {
                                var calc_height = 22
                                if (ttext.length <= 44 * 3) {
                                    calc_height = 22
                                } else if (ttext.length <= 44 * 4) {
                                    calc_height = 20
                                } else if (ttext.length < 44 * 5) {
                                    calc_height = 19
                                }
                                //int calc_height = 22 - cast(int)(1.1 * (calc_lines - 2));
                                //calc_height = max(19, min(calc_height, 22));
                                if (calc_height != font_height) {
                                    // 2, font_width, font_height, 0
                                    val patch = PATCH()
                                    run {
                                        patch.pos = line_pos
                                        patch.ops += BSS.OP(0x00, 0, arrayListOf(2))
                                        patch.ops += BSS.OP(0x00, 0, arrayListOf(calc_height))
                                        patch.ops += BSS.OP(0x00, 0, arrayListOf(calc_height))
                                        patch.ops += BSS.OP(0x00, 0, arrayListOf(0))
                                        patch.ops += BSS.OP(0x3F, 0, arrayListOf(4))
                                        patch.ops += BSS.OP(0x1_4D, 0, arrayListOf())
                                    }
                                    patches += patch
                                    font_height = calc_height
                                }
                            }
                        }
                    }
                }
                0x0_22 -> {
                    val a = sstack.popi()
                    val b = sstack.popi()
                    pushes.length = pushes.length - 1
                    pushes.length = pushes.length - 1
                    sstack.push(a * b)
                }
                BSS.Opcodes.TEXT_SIZE -> {
                    //writefln("TEXT_SIZE: %s", sstack);
                    font_width = sstack.int(0)
                    font_height = sstack.int(1)
                }
                else -> {

                }
            }
            last_op_type = op.type
        }

        var disp = 0
        for (patch in patches) {
            ops = BSS.insert(patch.pos + disp, ops, patch.ops)
            disp += patch.ops.length
        }

        // Fix pointers.
        var size = 0
        val translate = LinkedHashMap<Int, Int>()
        for (op in ops) {
            translate[op.ori_pos] = size
            //writefln("%d, %d", op.ori_pos, size);
            size += op.byteSize
        }
        var pos = 0
        for (op in ops) {
            pos += op.byteSize
            //if (op.type == 0x11) op.i[1] = size - pos;
            for ((k, t) in op.args.withIndex()) {
                try {
                    if (t is BSS.Pointer) {
                        op.args[k] = BSS.Pointer(translate[t.addr]!!)
                        //writefln("Update!");
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
        return ops
    }

    fun extract(ops: List<BSS.OP>): ACME {
        val acme = ACME()
        val sstack = BssStack()
        var line: Int = 0
        var line_pos = 0

        for ((pos, op) in ops.withIndex()) {
            when (op.type) {
                BSS.Opcodes.SCRIPT_LINE -> {
                    line = op.int(1)
                    line_pos = pos + 1
                    sstack.clear()
                }
                BSS.Opcodes.PUSH_INT -> {
                    sstack.push(op.int(0))
                }
                BSS.Opcodes.PUSH_STR -> {
                    //println("03: ${op.s[0]}");
                    sstack.push(op.str(0))
                }
                BSS.Opcodes.TEXT_PUT -> {
                    //println("TEXT_WRITE");
                    val text = sstack.str(sstack.args.size - 1) ?: ""
                    val title = sstack.str(sstack.args.size - 2)?.trim() ?: ""
                    val r = if (title.isNotEmpty()) "{$title}\n$text" else text

                    //println(" ## $title");
                    acme.add(line, r)
                }
                else -> Unit
            }
        }

        return acme
    }
}
