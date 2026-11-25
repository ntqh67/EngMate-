package com.engmate.app.sync

import org.junit.Assert.*
import org.junit.Test

class CardSyncTest {
    private fun card(word: String, meaning: String = "m-$word", example: String? = null, phonetic: String? = null) = CardData(word, meaning, example, phonetic)
    private fun local(id: Long, data: CardData) = LocalCard(id, data)

    @Test fun identicalListsNeedNoChange() {
        val plan = planCardSync(listOf(local(1, card("apple")), local(2, card("pear"))), listOf(card("pear"), card("apple")))
        assertTrue(plan.isEmpty)
    }

    @Test fun cardRemovedOnAnotherDeviceIsDeletedHere() {
        val plan = planCardSync(listOf(local(1, card("apple")), local(2, card("pear"))), listOf(card("apple")))
        assertEquals(listOf(2L), plan.delete)
        assertTrue(plan.update.isEmpty() && plan.add.isEmpty())
    }

    @Test fun emptyCloudListDeletesEveryCard() {
        val plan = planCardSync(listOf(local(1, card("apple")), local(2, card("pear"))), emptyList())
        assertEquals(listOf(1L, 2L), plan.delete)
    }

    @Test fun newCloudCardIsAdded() {
        val plan = planCardSync(listOf(local(1, card("apple"))), listOf(card("apple"), card("kiwi")))
        assertEquals(listOf(card("kiwi")), plan.add)
        assertTrue(plan.delete.isEmpty() && plan.update.isEmpty())
    }

    @Test fun changedMeaningIsUpdatedInPlaceKeepingTheRowId() {
        val plan = planCardSync(listOf(local(7, card("apple", meaning = "old"))), listOf(card("apple", meaning = "new")))
        assertEquals(listOf(7L to card("apple", meaning = "new")), plan.update)
        assertTrue(plan.delete.isEmpty() && plan.add.isEmpty())
    }

    @Test fun wordsMatchIgnoringCaseAndCaseChangeIsAnUpdate() {
        val plan = planCardSync(listOf(local(1, card("Apple"))), listOf(card("apple")))
        assertEquals(listOf(1L to card("apple")), plan.update)
        assertTrue(plan.delete.isEmpty() && plan.add.isEmpty())
    }

    @Test fun duplicateLocalWordKeepsTheFirstRowOnly() {
        val plan = planCardSync(listOf(local(1, card("apple")), local(2, card("apple"))), listOf(card("apple")))
        assertEquals(listOf(2L), plan.delete)
        assertTrue(plan.update.isEmpty() && plan.add.isEmpty())
    }

    @Test fun duplicateCloudWordIsAddedOnce() {
        val plan = planCardSync(emptyList(), listOf(card("apple"), card("apple", meaning = "other")))
        assertEquals(listOf(card("apple")), plan.add)
    }

    @Test fun mixedChangesAreAllPlanned() {
        val plan = planCardSync(
            listOf(local(1, card("a")), local(2, card("b", meaning = "old")), local(3, card("c"))),
            listOf(card("b", meaning = "new"), card("c"), card("d")))
        assertEquals(listOf(1L), plan.delete)
        assertEquals(listOf(2L to card("b", meaning = "new")), plan.update)
        assertEquals(listOf(card("d")), plan.add)
    }
}
