package com.engmate.app.feature.notebook.data

import com.engmate.app.core.sync.HandbookGuard
import com.engmate.app.feature.notebook.domain.HandbookMissing
import com.engmate.app.feature.notebook.domain.HandbookRepository

/**
 * Before a handbook is changed, asks whether another device already deleted it. If so the change is refused with
 * [HandbookMissing], so a stale copy is not written back over the deletion. Creating and deleting are not checked.
 */
class GuardedHandbookRepository(private val inner: HandbookRepository, private val guard: HandbookGuard) : HandbookRepository by inner {
    private suspend fun ensureAlive(handbookId: Long) { if (guard.deletedElsewhere(handbookId)) throw HandbookMissing() }

    override suspend fun rename(uid: String, handbookId: Long, title: String) { ensureAlive(handbookId); inner.rename(uid, handbookId, title) }
    override suspend fun addWord(uid: String, handbookId: Long, word: String) { ensureAlive(handbookId); inner.addWord(uid, handbookId, word) }
    override suspend fun removeWord(uid: String, handbookId: Long, word: String) { ensureAlive(handbookId); inner.removeWord(uid, handbookId, word) }
}
