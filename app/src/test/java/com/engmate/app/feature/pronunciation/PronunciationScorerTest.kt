package com.engmate.app.feature.pronunciation

import com.engmate.app.feature.pronunciation.domain.PronunciationScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationScorerTest {
    @Test fun perfectMatchIgnoresCaseAndPunctuation() {
        val r = PronunciationScorer.score("I drink coffee every morning.", "i drink Coffee every morning")
        assertEquals(100, r.score); assertTrue(r.words.all { it.matched })
    }
    @Test fun missingWordLowersScoreAndIsMarked() {
        val r = PronunciationScorer.score("She doesn't eat meat.", "she eat meat")
        assertEquals(75, r.score)
        assertFalse(r.words.first { it.word == "doesnt" }.matched)
    }
    @Test fun differentSentenceScoresZero() = assertEquals(0, PronunciationScorer.score("Good morning", "thank you very much").score)
    @Test fun extraWordsDoNotHelp() = assertEquals(100, PronunciationScorer.score("I am happy", "well I am very happy today").score)
    @Test fun oneLetterSlipInLongWordStillCounts() = assertEquals(100, PronunciationScorer.score("beautiful garden", "beautifull garden").score)
    @Test fun bestPicksHighestAlternative() {
        val best = PronunciationScorer.best("see you tomorrow", listOf("sea you to more", "see you tomorrow"))!!
        assertEquals("see you tomorrow", best.first)
    }
    @Test fun emptyTargetScoresZero() = assertEquals(0, PronunciationScorer.score("", "hello").score)
}
