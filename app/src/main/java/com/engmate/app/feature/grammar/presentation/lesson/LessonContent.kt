package com.engmate.app.feature.grammar.presentation.lesson

/** One block of a lesson page, cut from the lesson's plain-text content. */
sealed interface LessonBlock {
    /** "Cấu trúc": one row per sentence form; [label] is null for an unlabeled formula line. */
    data class Formula(val rows: List<Pair<String?, String>>) : LessonBlock
    /** A titled list of points, e.g. "Cách dùng" or "So sánh với will". [lead] is text on the heading line itself. */
    data class Points(val title: String, val lead: String?, val items: List<String>) : LessonBlock
    /** "Dấu hiệu": short markers shown as chips. */
    data class Markers(val items: List<String>) : LessonBlock
    /** "Lưu ý", "Mẹo" and spelling rules, shown as a callout. */
    data class Note(val title: String, val text: String, val items: List<String>) : LessonBlock
    /** Text outside any labeled section. */
    data class Paragraph(val text: String) : LessonBlock
}

/**
 * Splits lesson content into blocks. A line "Nhãn: …" opens a section and the following "• …" lines belong to it.
 * Content without any recognised label comes back as plain paragraphs, so nothing is ever hidden.
 */
object LessonContent {
    private val heading = Regex("^([^:•]{2,40}):\\s*(.*)$")
    private val formLabels = listOf("Khẳng định", "Phủ định", "Nghi vấn")

    private fun kind(title: String): String {
        val t = title.lowercase()
        return when {
            t.startsWith("cấu trúc") -> "formula"
            t.startsWith("dấu hiệu") -> "markers"
            t.startsWith("lưu ý") || t.startsWith("mẹo") || t.startsWith("quy tắc") -> "note"
            else -> "points"
        }
    }

    fun parse(content: String): List<LessonBlock> {
        val blocks = mutableListOf<LessonBlock>()
        var title: String? = null
        var lead: String? = null
        val items = mutableListOf<String>()
        val loose = mutableListOf<String>()

        fun flushLoose() {
            if (loose.isNotEmpty()) { blocks += LessonBlock.Paragraph(loose.joinToString("\n")); loose.clear() }
        }
        fun flushSection() {
            val t = title ?: return
            val l = lead?.takeIf { it.isNotBlank() }
            blocks += when (kind(t)) {
                "formula" -> LessonBlock.Formula((listOfNotNull(l) + items).map { line ->
                    val label = formLabels.firstOrNull { line.startsWith("$it:") }
                    if (label == null) null to line else label to line.removePrefix("$label:").trim()
                })
                "markers" -> LessonBlock.Markers((listOfNotNull(l) + items).joinToString(", ").trimEnd('.')
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() })
                "note" -> LessonBlock.Note(t, l.orEmpty(), items.toList())
                else -> LessonBlock.Points(t, l, items.toList())
            }
            title = null; lead = null; items.clear()
        }

        for (raw in content.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val bullet = line.startsWith("•")
            val match = if (bullet) null else heading.find(line)
            when {
                match != null -> {
                    flushSection(); flushLoose()
                    title = match.groupValues[1].trim(); lead = match.groupValues[2].trim()
                }
                bullet && title != null -> items += line.removePrefix("•").trim()
                title != null -> items += line
                else -> loose += line
            }
        }
        flushSection(); flushLoose()
        return blocks
    }

    /** Minutes to read: 150 words per minute, at least 1. */
    fun readingMinutes(content: String): Int = content.split(Regex("\\s+")).count { it.isNotBlank() } / 150 + 1

    /** "Hiện tại đơn (Present Simple)" → "Hiện tại đơn" and "Present Simple". */
    fun splitTitle(title: String): Pair<String, String?> {
        // A bracketed list ("am, is, are") is part of the name, not a translation.
        val m = Regex("^(.+?)\\s*\\(([^(),]+)\\)\\s*$").find(title) ?: return title to null
        return m.groupValues[1] to m.groupValues[2]
    }
}
