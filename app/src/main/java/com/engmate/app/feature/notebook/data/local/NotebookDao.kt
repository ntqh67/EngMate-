package com.engmate.app.feature.notebook.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class NotebookEntry(
    @Embedded val entry: NotebookWordEntity,
    @Relation(parentColumn = "word_id", entityColumn = "id")
    val word: com.engmate.app.feature.vocabulary.data.local.WordEntity,
)

@Dao
abstract class NotebookDao {
    @Insert abstract suspend fun add(entry: NotebookWordEntity): Long
    @Transaction
    @Query("SELECT n.* FROM notebook_words n JOIN words w ON n.word_id = w.id WHERE n.user_uid = :uid AND (instr(lower(w.word), lower(trim(:query))) > 0 OR instr(coalesce(w.meaning_vi, ''), trim(:query)) > 0) ORDER BY n.created_at DESC, n.id DESC")
    abstract fun observe(uid: String, query: String = ""): Flow<List<NotebookEntry>>
    @Query("SELECT * FROM notebook_words WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun get(uid: String, wordId: Long): NotebookWordEntity?
    @Query("UPDATE notebook_words SET note = :note WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun updateNote(uid: String, wordId: Long, note: String?): Int
    @Query("DELETE FROM notebook_words WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun remove(uid: String, wordId: Long): Int

    @Query("SELECT * FROM word_notes WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun getWordNote(uid: String, wordId: Long): WordNoteEntity?
    @Insert protected abstract suspend fun insertWordNote(note: WordNoteEntity): Long
    @Update protected abstract suspend fun updateWordNote(note: WordNoteEntity): Int
    @Transaction
    open suspend fun saveWordNote(note: WordNoteEntity) {
        val existing = getWordNote(note.userUid, note.wordId)
        if (existing == null) insertWordNote(note.copy(id = 0))
        else updateWordNote(note.copy(id = existing.id))
    }
    @Query("DELETE FROM word_notes WHERE user_uid = :uid AND word_id = :wordId")
    abstract suspend fun deleteWordNote(uid: String, wordId: Long): Int
}
