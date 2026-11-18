package com.engmate.app.core.sync

/**
 * Keeps the local Room copy of shared content (grammar lessons, questions) in line with the remote source.
 * The demo flavor has no remote source and binds a no-op; the live flavor syncs with Cloud Firestore.
 */
interface ContentSync {
    /** Starts syncing for the lifetime of the process. Safe to call more than once. */
    fun start()
}
