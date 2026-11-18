package com.engmate.app.core.contracts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class TopicRef(val id: Long, val title: String)

/**
 * Vocabulary topic names for the quiz module (question bank, history titles).
 * Bound in di/IntegrationContractsModule; the default is [EmptyTopicLookup].
 */
interface TopicLookup {
    /** Khang implements it from the topics table. Must emit at least once (an empty list is fine). */
    fun observeTopics(): Flow<List<TopicRef>>
    suspend fun titleOf(topicId: Long): String?
}

/** Default until the topics table exists: no topics, so screens fall back to the numeric topic id. */
class EmptyTopicLookup : TopicLookup {
    override fun observeTopics(): Flow<List<TopicRef>> = flowOf(emptyList())
    override suspend fun titleOf(topicId: Long): String? = null
}
