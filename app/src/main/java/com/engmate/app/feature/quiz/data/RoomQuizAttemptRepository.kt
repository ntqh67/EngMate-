package com.engmate.app.feature.quiz.data

import com.engmate.app.core.contracts.EmptyTopicLookup
import com.engmate.app.core.contracts.NoOpProgressRecorder
import com.engmate.app.core.contracts.ProgressRecorder
import com.engmate.app.core.contracts.TopicLookup
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.profile.domain.ProfileInput
import com.engmate.app.feature.profile.domain.ProfileRepository
import com.engmate.app.feature.quiz.data.local.*
import com.engmate.app.feature.quiz.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomQuizAttemptRepository @Inject constructor(
    private val dao: QuizAttemptDao,
    private val questions: QuestionDao,
    private val profiles: ProfileRepository,
    private val session: UserSession,
    private val clock: Clock,
    private val progressRecorder: ProgressRecorder = NoOpProgressRecorder(),
    private val topicLookup: TopicLookup = EmptyTopicLookup(),
) : QuizAttemptRepository {
    private fun owner(uid: String) {
        if (uid.isBlank() || session.user.value?.uid != uid) throw AttemptSessionRequired()
    }

    /** Topic names by id; a failing lookup only costs the names, never the history. */
    private fun topicTitles(): Flow<Map<Long, String>> = topicLookup.observeTopics()
        .map { topics -> topics.associate { it.id to it.title } }
        .catch { emit(emptyMap()) }

    override fun observeHistory(uid: String): Flow<List<AttemptSummary>> {
        owner(uid)
        return combine(dao.observeForUser(uid), topicTitles()) { rows, titles -> rows.map { it.toSummary(titles) } }
    }

    override fun observeAttempt(uid: String, id: Long): Flow<AttemptDetail?> {
        owner(uid)
        return combine(dao.observeWithAnswers(uid, id), topicTitles()) { row, titles -> row?.toDetail(titles) }
    }

    override suspend fun saveAttempt(uid: String, draft: AttemptDraft): AttemptResult {
        owner(uid)
        val category = QuestionCategory.of(draft.topicId, draft.lessonId, draft.isPlacement)
            ?: throw InvalidAttempt("Lượt làm phải thuộc đúng một loại: chủ đề, bài ngữ pháp hoặc kiểm tra trình độ")
        if (draft.answers.isEmpty()) throw InvalidAttempt("Lượt làm cần ít nhất 1 câu trả lời")
        if (draft.answers.map { it.questionId }.toSet().size != draft.answers.size) throw InvalidAttempt("Mỗi câu hỏi chỉ được trả lời một lần")
        if (category == QuestionCategory.PLACEMENT && draft.answers.size != QuizScoring.PLACEMENT_QUESTION_COUNT)
            throw InvalidAttempt("Bài kiểm tra trình độ cần đủ ${QuizScoring.PLACEMENT_QUESTION_COUNT} câu")
        val profile = profiles.observe(uid).first() ?: throw ProfileRequired()

        val byId = questions.getAll(draft.answers.map { it.questionId }).associate { it.question.id to it.toDomain() }
        val graded = draft.answers.map { answer ->
            val question = byId[answer.questionId] ?: throw InvalidAttempt("Câu hỏi #${answer.questionId} không còn tồn tại")
            if (question.category != category) throw InvalidAttempt("Câu hỏi #${question.id} không thuộc bài đang làm")
            if (category == QuestionCategory.GRAMMAR && question.lessonId != draft.lessonId ||
                category == QuestionCategory.VOCABULARY && question.topicId != draft.topicId)
                throw InvalidAttempt("Câu hỏi #${question.id} không thuộc bài đang làm")
            val selected = answer.selectedOptionId?.takeIf { id -> question.options.any { it.id == id } }
            if (answer.selectedOptionId != null && selected == null) throw InvalidAttempt("Đáp án đã chọn không thuộc câu hỏi #${question.id}")
            val text = answer.answerText?.let(QuizScoring::collapseSpaces)?.ifEmpty { null }
            AttemptAnswerEntity(attemptId = 0, questionId = question.id,
                selectedOptionId = selected.takeIf { question.answerType == AnswerType.MULTIPLE_CHOICE },
                answerText = text.takeIf { question.answerType == AnswerType.FILL_BLANK },
                isCorrect = QuizScoring.grade(question, selected, text))
        }
        val correct = graded.count { it.isCorrect }
        val xp = QuizScoring.xp(correct)
        val now = clock.millis()
        val id = dao.saveAttempt(QuizAttemptEntity(userUid = uid, topicId = draft.topicId, lessonId = draft.lessonId,
            isPlacement = draft.isPlacement, correctCount = correct, totalQuestions = graded.size, xpEarned = xp,
            completedAt = now, updatedAt = now), graded)

        // The attempt is stored; progress bookkeeping must not undo or hide that.
        try {
            progressRecorder.onQuizCompleted(uid, xp, correct, graded.size, draft.isPlacement, now)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { }

        val level = if (category == QuestionCategory.PLACEMENT) QuizScoring.levelFromScore(correct) else null
        var levelSaved = level != null
        if (level != null && level != profile.englishLevel) {
            // Uses the profile owner's public API; the attempt stays stored even if this fails.
            levelSaved = try {
                profiles.update(uid, ProfileInput(profile.displayName, profile.photoUrl.orEmpty(), level, profile.dailyGoalMinutes.toString()))
                true
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        }
        return AttemptResult(id, correct, graded.size, xp, level, levelSaved)
    }

    override suspend fun delete(uid: String, id: Long) {
        owner(uid)
        if (dao.delete(uid, id) == 0) throw AttemptMissing()
    }
}
