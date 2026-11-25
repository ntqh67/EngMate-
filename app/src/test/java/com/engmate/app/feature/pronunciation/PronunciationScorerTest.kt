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
    @Test fun extraWordsLowerTheScore() {
        val r = PronunciationScorer.score("I am happy", "well I am very happy today")
        assertEquals(50, r.score) // 3 matched / (3 target + 3 extra)
        assertEquals(listOf("well", "very", "today"), r.extra)
    }
    @Test fun repeatedWordIsNotPerfect() {
        val r = PronunciationScorer.score("boy", "boy boy")
        assertEquals(50, r.score)
        assertTrue(r.words.single().matched)
        assertEquals(listOf("boy"), r.extra)
    }
    // "eat" is not "eats" (short words must match exactly): 2 matched / (3 target + 2 extra).
    @Test fun missingAndExtraBothCount() = assertEquals(40, PronunciationScorer.score("she eats apples", "she eat eat apples").score)
    @Test fun oneLetterSlipInLongWordStillCounts() = assertEquals(100, PronunciationScorer.score("beautiful garden", "beautifull garden").score)
    @Test fun bestPicksHighestAlternative() {
        val best = PronunciationScorer.best("see you tomorrow", listOf("sea you to more", "see you tomorrow"))!!
        assertEquals("see you tomorrow", best.first)
    }
    @Test fun emptyTargetScoresZero() = assertEquals(0, PronunciationScorer.score("", "hello").score)
}
