package com.engmate.app.feature.pronunciation.domain

data class WordMatch(val word: String, val matched: Boolean)
data class PronunciationResult(val score: Int, val words: List<WordMatch>)

/**
 * Compares what the speech recognizer heard with the target sentence, word by word: the score is the share of target words found
 * in order (longest common subsequence), so extra or repeated words do not help and a missed word lowers the score.
 * A one-letter difference in a long word still counts, because recognizers often drop or add a letter.
 */
object PronunciationScorer {
    fun tokens(text: String): List<String> =
        text.lowercase().replace("'", "").replace("’", "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    private fun same(a: String, b: String): Boolean = a == b || (a.length >= 5 && b.length >= 5 && editDistance(a, b) <= 1)

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1).also { it[0] = i }
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    fun score(target: String, spoken: String): PronunciationResult {
        val want = tokens(target)
        val heard = tokens(spoken)
        if (want.isEmpty()) return PronunciationResult(0, emptyList())
        val lcs = Array(want.size + 1) { IntArray(heard.size + 1) }
        for (i in 1..want.size) for (j in 1..heard.size)
            lcs[i][j] = if (same(want[i - 1], heard[j - 1])) lcs[i - 1][j - 1] + 1 else maxOf(lcs[i - 1][j], lcs[i][j - 1])
        val matched = BooleanArray(want.size)
        var i = want.size; var j = heard.size
        while (i > 0 && j > 0) when {
            same(want[i - 1], heard[j - 1]) && lcs[i][j] == lcs[i - 1][j - 1] + 1 -> { matched[i - 1] = true; i--; j-- }
            lcs[i - 1][j] >= lcs[i][j - 1] -> i--
            else -> j--
        }
        return PronunciationResult(Math.round(100f * lcs[want.size][heard.size] / want.size), want.mapIndexed { n, w -> WordMatch(w, matched[n]) })
    }

    /** Picks the best of the recognizer's alternatives. */
    fun best(target: String, alternatives: List<String>): Pair<String, PronunciationResult>? =
        alternatives.map { it to score(target, it) }.maxByOrNull { it.second.score }
}
