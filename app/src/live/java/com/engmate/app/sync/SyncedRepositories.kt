package com.engmate.app.sync

import com.engmate.app.feature.grammar.data.RoomGrammarRepository
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.grammar.domain.LessonInput
import com.engmate.app.feature.quiz.data.RoomQuestionRepository
import com.engmate.app.feature.quiz.data.local.QuestionDao
import com.engmate.app.feature.quiz.domain.QuestionInput
import com.engmate.app.feature.quiz.domain.QuestionRepository
import javax.inject.Inject

/**
 * Admin edits made in the app: validate and save in Room exactly as before (same errors, same rules),
 * then push the saved row to Firestore so other devices and the Firebase console see it.
 */
class SyncedGrammarRepository @Inject constructor(
    private val local: RoomGrammarRepository,
    private val dao: GrammarLessonDao,
    private val questionDao: QuestionDao,
    private val remote: FirestoreContent,
) : GrammarRepository by local {
    override suspend fun create(input: LessonInput): Long = local.create(input).also { push(it) }

    override suspend fun update(id: Long, input: LessonInput) {
        local.update(id, input)
        push(id)
    }

    override suspend fun delete(id: Long) {
        val questionIds = questionDao.forLesson(id).map { it.question.id }
        local.delete(id)
        remote.deleteLesson(id, questionIds)
    }

    private suspend fun push(id: Long) = dao.get(id)?.let(remote::putLesson)
}

class SyncedQuestionRepository @Inject constructor(
    private val local: RoomQuestionRepository,
    private val dao: QuestionDao,
    private val remote: FirestoreContent,
) : QuestionRepository by local {
    override suspend fun create(input: QuestionInput): Long = local.create(input).also { push(it) }

    override suspend fun update(id: Long, input: QuestionInput) {
        local.update(id, input)
        push(id)
    }

    override suspend fun delete(id: Long) {
        local.delete(id)
        remote.deleteQuestion(id)
    }

    private suspend fun push(id: Long) = dao.get(id)?.let(remote::putQuestion)
}
