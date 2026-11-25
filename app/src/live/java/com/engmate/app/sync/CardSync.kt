package com.engmate.app.sync

internal data class CardData(val word: String, val meaning: String, val example: String?, val phonetic: String?)
internal data class LocalCard(val id: Long, val data: CardData)
internal data class CardPlan(val delete: List<Long>, val update: List<Pair<Long, CardData>>, val add: List<CardData>) {
    val isEmpty get() = delete.isEmpty() && update.isEmpty() && add.isEmpty()
}

/**
 * What to change so the cards of one local collection match the cloud list. Cards are matched by word, ignoring case,
 * because their row ids differ between devices. A word listed twice on either side is kept once.
 */
internal fun planCardSync(local: List<LocalCard>, cloud: List<CardData>): CardPlan {
    val wanted = LinkedHashMap<String, CardData>()
    cloud.forEach { wanted.putIfAbsent(it.word.lowercase(), it) }
    val kept = HashSet<String>()
    val delete = ArrayList<Long>()
    val update = ArrayList<Pair<Long, CardData>>()
    for (card in local) {
        val key = card.data.word.lowercase()
        val target = wanted[key]
        when {
            target == null || !kept.add(key) -> delete += card.id
            target != card.data -> update += card.id to target
        }
    }
    return CardPlan(delete, update, wanted.filterKeys { it !in kept }.values.toList())
}
