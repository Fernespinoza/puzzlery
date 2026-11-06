package io.github.nandomango702.stpuzzlerelay

import kotlin.math.abs

/**
 * Turns a DescribeRecorder capture into the readable board
 * description Fernando chose (2026-10-31): full rows, every cell
 * spoken, "blank" for empty cells.
 *
 * The engine draws cell contents as text on a lattice, so the
 * formatter clusters the captured coordinates back into rows and
 * columns. The largest font size with a real presence (≥3 texts)
 * is the "primary" text (placed digits, clue numbers); anything
 * smaller that lands in an otherwise empty cell is read as the
 * cell's small text — pencil marks in Solo (its candidates).
 * Games whose text doesn't sit on a grid still come out
 * positionally truthful — the lattice is inferred from the texts
 * themselves, never assumed.
 */
object DescribeFormatter {

    fun format(
        context: String?,
        status: String?,
        texts: List<DescribeRecorder.DrawnText>,
        notesArePencil: Boolean,
    ): String {
        // No puzzle name here: the Describe dialog's title already
        // names the puzzle, and TalkBack would read it twice.
        val sb = StringBuilder()
        if (!context.isNullOrBlank()) sb.append(context)
        if (!status.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(status.trim())
        }
        if (texts.isEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("No text is drawn on this board.")
            return sb.toString()
        }
        // The board's main text (placed digits, clue numbers) is its
        // LARGEST text with a real presence — not the most common
        // size: on a worked Solo board, pencil marks can outnumber
        // the digits, and picking the modal size would promote the
        // marks to "primary" and shatter the lattice (the cluster
        // tolerance scales with the primary size). The count floor
        // keeps a stray large glyph or two from hijacking the role;
        // tiny captures fall back to the modal size.
        val sizeCounts = texts.groupingBy { it.size }.eachCount()
        val primarySize = sizeCounts.keys
            .filter { sizeCounts.getValue(it) >= 3 }
            .maxOrNull()
            ?: sizeCounts.maxByOrNull { it.value }!!.key
        val primary = texts.filter { it.size == primarySize }
        val notes = texts.filter { it.size != primarySize }
        val tol = primarySize * 0.6f
        val rowYs = cluster(primary.map { it.y }, tol)
        val colXs = cluster(primary.map { it.x }, tol)
        val cells = Array(rowYs.size) { arrayOfNulls<String>(colXs.size) }
        val pencils = Array(rowYs.size) { Array(colXs.size) { mutableListOf<String>() } }
        fun nearest(v: Int, centers: List<Float>): Int =
            centers.indices.minByOrNull { abs(centers[it] - v) } ?: 0
        primary.forEach { t ->
            cells[nearest(t.y, rowYs)][nearest(t.x, colXs)] = t.text
        }
        notes.forEach { t ->
            val r = nearest(t.y, rowYs)
            val c = nearest(t.x, colXs)
            if (cells[r][c] == null) pencils[r][c] += t.text
        }
        // Smaller texts landing in an otherwise empty cell are notes
        // for that cell: pencil marks in Solo (other games have them
        // too — Keen, Towers, Unequal, Undead — but only Solo's are
        // called "pencil"), small printed text — Keen cage clues and
        // the like — everywhere else. Known mislabel: Killer Solo
        // draws its cage totals at the same small size, top-left of
        // the cage's first cell, so in an empty cell a total is read
        // as "pencil 12" (right number, wrong word) and in a filled
        // cell it is dropped entirely. Telling totals from marks
        // needs the colour work in Phase 2.
        val noteWord = if (notesArePencil) "pencil" else "small"
        for (r in rowYs.indices) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Row ").append(r + 1).append(": ")
            val parts = colXs.indices.map { c ->
                cells[r][c]
                    ?: if (pencils[r][c].isNotEmpty()) {
                        noteWord + " " + pencils[r][c].sorted().joinToString(" ")
                    } else {
                        "blank"
                    }
            }
            sb.append(parts.joinToString(", "))
        }
        return sb.toString()
    }

    /** Groups sorted coordinates into cluster centres, splitting where the gap exceeds [tol]. */
    private fun cluster(values: List<Int>, tol: Float): List<Float> {
        val sorted = values.sorted()
        val out = mutableListOf<Float>()
        var group = mutableListOf<Int>()
        fun flush() {
            if (group.isNotEmpty()) {
                out += group.average().toFloat()
                group = mutableListOf()
            }
        }
        for (v in sorted) {
            if (group.isNotEmpty() && v - group.last() > tol) flush()
            group += v
        }
        flush()
        return out
    }
}
