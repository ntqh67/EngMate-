package com.engmate.app.feature.quiz.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.engmate.app.feature.grammar.data.local.GrammarConverters
import com.engmate.app.feature.grammar.domain.GrammarExample
import com.engmate.app.feature.quiz.domain.AnswerType

/** Seeds sample content once: on a fresh install ([Callback.onCreate]) or when upgrading from v1 ([Migration1To2]). */
object GrammarQuizSeed {
    class Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = insertInto(db)
    }

    class Migration1To2 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) = insertInto(db)
    }

    private data class SeedQuestion(val type: AnswerType, val content: String, val explanation: String, val options: List<Pair<String, Boolean>>)
    private data class SeedLesson(val title: String, val level: String, val summary: String, val content: String,
                                  val examples: List<GrammarExample>, val questions: List<SeedQuestion>)

    private fun mc(content: String, options: List<String>, correct: Int, explanation: String) =
        SeedQuestion(AnswerType.MULTIPLE_CHOICE, content, explanation, options.mapIndexed { i, o -> o to (i == correct) })

    private fun fb(content: String, accepted: List<String>, explanation: String) =
        SeedQuestion(AnswerType.FILL_BLANK, content, explanation, accepted.map { it to true })

    fun insertInto(db: SupportSQLiteDatabase) {
        val converters = GrammarConverters()
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            lessons.forEachIndexed { index, lesson ->
                val lessonId = db.insert("grammar_lessons", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                    put("title", lesson.title); put("level", lesson.level); put("summary", lesson.summary)
                    put("content", lesson.content); put("examples", converters.examplesToJson(lesson.examples))
                    put("order_index", index); put("updated_at", now)
                })
                lesson.questions.forEach { insertQuestion(db, it, lessonId = lessonId, isPlacement = false, now = now) }
            }
            placement.forEach { insertQuestion(db, it, lessonId = null, isPlacement = true, now = now) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertQuestion(db: SupportSQLiteDatabase, q: SeedQuestion, lessonId: Long?, isPlacement: Boolean, now: Long) {
        val questionId = db.insert("questions", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            if (lessonId == null) putNull("lesson_id") else put("lesson_id", lessonId)
            put("is_placement", if (isPlacement) 1 else 0)
            put("answer_type", q.type.name); put("content", q.content); put("explanation", q.explanation)
            put("created_at", now)
        })
        q.options.forEachIndexed { i, (content, correct) ->
            db.insert("question_options", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("question_id", questionId); put("content", content)
                put("is_correct", if (correct) 1 else 0); put("order_index", i)
            })
        }
    }

    private val lessons = listOf(
        SeedLesson(
            "Hiện tại đơn (Present Simple)", "A1",
            "Diễn tả thói quen, sự thật hiển nhiên và lịch trình cố định.",
            """Cấu trúc:
• Khẳng định: S + V(s/es). Với he/she/it, động từ thêm -s hoặc -es.
• Phủ định: S + do/does + not + V.
• Nghi vấn: Do/Does + S + V?
Dấu hiệu: always, usually, often, sometimes, every day, on Mondays.
Quy tắc thêm -es: động từ tận cùng -o, -s, -sh, -ch, -x, -z (goes, watches); phụ âm + y → -ies (studies).""",
            listOf(GrammarExample("I drink coffee every morning.", "Tôi uống cà phê mỗi sáng."),
                GrammarExample("She doesn't eat meat.", "Cô ấy không ăn thịt."),
                GrammarExample("The sun rises in the east.", "Mặt trời mọc ở hướng đông.")),
            listOf(
                mc("She ___ to school every day.", listOf("go", "goes", "going", "gone"), 1, "Chủ ngữ ngôi thứ ba số ít (she) → động từ thêm -es: goes."),
                fb("He ___ (watch) TV every evening.", listOf("watches"), "Động từ tận cùng -ch thêm -es: watches."),
                mc("___ you like coffee?", listOf("Do", "Does", "Are", "Is"), 0, "Câu hỏi với chủ ngữ you dùng trợ động từ Do."),
                mc("My father ___ not smoke.", listOf("do", "does", "is", "are"), 1, "Phủ định ngôi thứ ba số ít: does not + động từ nguyên mẫu."),
                fb("They ___ (live) in Hanoi.", listOf("live"), "Chủ ngữ số nhiều (they) dùng động từ nguyên mẫu."),
                mc("Water ___ at 100°C.", listOf("boil", "boils", "is boiling", "boiled"), 1, "Sự thật hiển nhiên dùng hiện tại đơn; water là danh từ không đếm được nên dùng boils."),
                fb("She ___ (study) English on Mondays.", listOf("studies"), "Phụ âm + y → đổi y thành i rồi thêm -es: studies."),
                mc("How often ___ she play tennis?", listOf("do", "does", "is", "has"), 1, "Câu hỏi với she dùng does; động từ chính giữ nguyên mẫu."),
                fb("I usually ___ (get) up at 6 a.m.", listOf("get"), "Chủ ngữ I dùng động từ nguyên mẫu; usually là dấu hiệu hiện tại đơn."),
                mc("Which sentence is correct?", listOf("He don't like milk.", "He doesn't likes milk.", "He doesn't like milk.", "He not like milk."), 2, "Phủ định: doesn't + động từ nguyên mẫu (like, không phải likes)."),
            ),
        ),
        SeedLesson(
            "Hiện tại tiếp diễn (Present Continuous)", "A1",
            "Diễn tả hành động đang xảy ra tại thời điểm nói.",
            """Cấu trúc:
• Khẳng định: S + am/is/are + V-ing.
• Phủ định: S + am/is/are + not + V-ing.
• Nghi vấn: Am/Is/Are + S + V-ing?
Dấu hiệu: now, right now, at the moment, Look!, Listen!
Quy tắc thêm -ing: bỏ e câm (write → writing); gấp đôi phụ âm cuối với động từ một âm tiết dạng phụ âm–nguyên âm–phụ âm (run → running).
Động từ chỉ trạng thái (know, like, want…) thường không dùng ở thì tiếp diễn.""",
            listOf(GrammarExample("I am reading a book now.", "Bây giờ tôi đang đọc sách."),
                GrammarExample("They aren't playing football at the moment.", "Lúc này họ không chơi bóng đá."),
                GrammarExample("Is it raining outside?", "Bên ngoài trời có đang mưa không?")),
            listOf(
                mc("Look! The baby ___.", listOf("sleeps", "is sleeping", "sleep", "are sleeping"), 1, "\"Look!\" báo hiệu hành động đang xảy ra: is + V-ing."),
                fb("They ___ (play) football now.", listOf("are playing"), "now → hiện tại tiếp diễn; they đi với are."),
                mc("I ___ reading a book at the moment.", listOf("am", "is", "are", "be"), 0, "Chủ ngữ I đi với am."),
                fb("She ___ (write) a letter right now.", listOf("is writing"), "write bỏ e trước khi thêm -ing: writing."),
                mc("___ you listening to me?", listOf("Do", "Is", "Are", "Am"), 2, "Câu hỏi với you: Are + you + V-ing?"),
                fb("We ___ (not / watch) TV at the moment.", listOf("are not watching", "aren't watching"), "Phủ định: are not (aren't) + V-ing."),
                mc("Choose the correct -ing form of \"run\".", listOf("runing", "running", "runeing", "runnning"), 1, "Động từ một âm tiết dạng phụ âm–nguyên âm–phụ âm: gấp đôi phụ âm cuối."),
                mc("Listen! Somebody ___ at the door.", listOf("knocks", "knock", "is knocking", "are knocking"), 2, "\"Listen!\" → hành động đang xảy ra; somebody đi với is."),
                fb("He ___ (swim) in the pool now.", listOf("is swimming"), "swim → swimming (gấp đôi m)."),
                mc("Which sentence is correct?", listOf("I am knowing the answer.", "I know the answer.", "I knowing the answer.", "I am know the answer."), 1, "know là động từ chỉ trạng thái, không dùng ở thì tiếp diễn."),
            ),
        ),
        SeedLesson(
            "Câu điều kiện loại 1 (First Conditional)", "B1",
            "Diễn tả điều kiện có thể xảy ra ở hiện tại hoặc tương lai và kết quả của nó.",
            """Cấu trúc: If + S + V (hiện tại đơn), S + will/can/may + V.
• Mệnh đề If có thể đứng trước hoặc sau; đứng trước thì có dấu phẩy.
• Unless = If … not; sau unless dùng động từ khẳng định.
• Không dùng will trong mệnh đề If.""",
            listOf(GrammarExample("If it rains, we will stay at home.", "Nếu trời mưa, chúng ta sẽ ở nhà."),
                GrammarExample("You will pass the exam if you study hard.", "Bạn sẽ đỗ nếu học chăm chỉ."),
                GrammarExample("Unless you hurry, you will miss the bus.", "Nếu không nhanh lên, bạn sẽ lỡ xe buýt.")),
            listOf(
                mc("If it rains, we ___ at home.", listOf("stay", "will stay", "would stay", "stayed"), 1, "Mệnh đề chính của câu điều kiện loại 1 dùng will + V."),
                fb("If you ___ (study) hard, you will pass the exam.", listOf("study"), "Mệnh đề If loại 1 dùng hiện tại đơn."),
                mc("If she ___ early, she will catch the bus.", listOf("leave", "leaves", "will leave", "left"), 1, "Mệnh đề If dùng hiện tại đơn; she → leaves."),
                fb("We will go to the beach if the weather ___ (be) nice.", listOf("is"), "Mệnh đề If dùng hiện tại đơn: the weather is."),
                mc("What ___ you do if you miss the train?", listOf("do", "will", "would", "are"), 1, "Câu hỏi ở mệnh đề chính: What will you do…?"),
                mc("I'll call you if I ___ time.", listOf("have", "will have", "had", "having"), 0, "Không dùng will trong mệnh đề If."),
                fb("If he doesn't hurry, he ___ (miss) the flight.", listOf("will miss"), "Mệnh đề chính: will + V."),
                mc("Unless you ___, you will be late.", listOf("hurry", "will hurry", "hurried", "don't hurry"), 0, "Unless = if not; sau unless dùng động từ khẳng định ở hiện tại đơn."),
                fb("If they ___ (not / come), we will start without them.", listOf("don't come", "do not come"), "Phủ định hiện tại đơn với they: don't + V."),
                mc("Which sentence is a first conditional?", listOf("If I were rich, I would travel.", "If I see him, I will tell him.", "If I had known, I would have come.", "If you heat water, it boils."), 1, "If + hiện tại đơn, will + V là câu điều kiện loại 1."),
            ),
        ),
    )

    /** Difficulty rises from A1 (1–4) through A2, B1 to B2 (13–15). */
    private val placement = listOf(
        mc("I ___ a student.", listOf("am", "is", "are", "be"), 0, "Chủ ngữ I đi với am."),
        mc("There ___ two books on the table.", listOf("is", "are", "be", "am"), 1, "There are + danh từ số nhiều."),
        mc("She ___ breakfast at 7 every morning.", listOf("have", "has", "having", "is have"), 1, "Hiện tại đơn với she: has."),
        fb("The plural of \"child\" is ___.", listOf("children"), "child là danh từ bất quy tắc: children."),
        mc("Yesterday I ___ to the cinema.", listOf("go", "goes", "went", "gone"), 2, "yesterday → quá khứ đơn: went."),
        mc("This bag is ___ than that one.", listOf("heavy", "heavier", "heaviest", "more heavy"), 1, "So sánh hơn của tính từ ngắn tận cùng -y: heavier."),
        fb("I have lived here ___ 2015. (for / since)", listOf("since"), "since + mốc thời gian."),
        mc("You ___ wear a seatbelt. It's the law.", listOf("must", "might", "can", "would"), 0, "must diễn tả sự bắt buộc."),
        mc("I ___ TV when the phone rang.", listOf("watched", "was watching", "am watching", "have watched"), 1, "Hành động đang diễn ra trong quá khứ thì bị xen ngang: quá khứ tiếp diễn."),
        mc("If I ___ you, I would accept the offer.", listOf("am", "was", "were", "be"), 2, "Câu điều kiện loại 2 dùng were cho mọi ngôi."),
        fb("The letter ___ (write) by my sister yesterday.", listOf("was written"), "Bị động quá khứ đơn: was/were + V3."),
        mc("She asked me where I ___.", listOf("live", "lived", "am living", "will live"), 1, "Câu tường thuật lùi thì: live → lived."),
        mc("By the time we arrived, the film ___.", listOf("started", "has started", "had started", "was starting"), 2, "Hành động xảy ra trước một mốc quá khứ: quá khứ hoàn thành."),
        fb("I'm looking forward to ___ (see) you.", listOf("seeing"), "look forward to + V-ing."),
        mc("Hardly ___ when it started to rain.", listOf("we had left", "had we left", "we left", "did we leave"), 1, "Hardly đứng đầu câu → đảo ngữ: had + S + V3."),
    )
}
