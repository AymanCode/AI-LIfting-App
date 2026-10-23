package com.ayman.ecolift.data.splits

private const val EPSILON = 1e-9

/** A cluster that passed the group test: members ascending, co-occurrence days ascending. */
internal data class LiftCluster(val members: List<Long>, val coOccurrenceDays: List<String>) {
    val key: String get() = "c:" + members.joinToString("-")
}

/** The learning half of the engine: held lifts, everyday lifts and the groups among the rest. */
internal data class LearnedClusters(
    val held: List<Long>,
    val everyday: Set<Long>,
    val clusters: List<LiftCluster>,
)

/** Lexicographic order on ascending id lists. */
internal val idListOrder: Comparator<List<Long>> = Comparator { a, b ->
    for (i in 0 until minOf(a.size, b.size)) {
        val c = a[i].compareTo(b[i])
        if (c != 0) return@Comparator c
    }
    a.size.compareTo(b.size)
}

/**
 * Overlap coefficient over the cluster-window days: days both lifts were trained divided by the
 * smaller of their day counts; zero shared days (or a lift with no days) is 0.
 */
internal fun affinity(a: Long, b: Long, days: List<String>, history: LiftHistory): Double {
    var countA = 0
    var countB = 0
    var shared = 0
    for (date in days) {
        val lifts = history.lifts(date)
        val hasA = a in lifts
        val hasB = b in lifts
        if (hasA) countA++
        if (hasB) countB++
        if (hasA && hasB) shared++
    }
    val smaller = minOf(countA, countB)
    return if (smaller == 0) 0.0 else shared.toDouble() / smaller
}

/** One possible merge: its mean pairwise affinity, the training days its union covers, its ids. */
internal data class MergeCandidate(val i: Int, val j: Int, val mean: Double, val coverage: Int, val unionIds: List<Long>)

/** The merge to make: highest mean, then larger coverage, then the lexicographically smaller ids. */
internal fun pickMerge(candidates: List<MergeCandidate>): MergeCandidate? =
    candidates.fold(null as MergeCandidate?) { best, next ->
        when {
            best == null -> next
            next.mean > best.mean + EPSILON -> next
            next.mean < best.mean - EPSILON -> best
            next.coverage != best.coverage -> if (next.coverage > best.coverage) next else best
            idListOrder.compare(next.unionIds, best.unionIds) < 0 -> next
            else -> best
        }
    }

/**
 * Average-linkage agglomerative clustering: start with one cluster per lift and repeatedly merge
 * the pair with the highest mean pairwise affinity while that mean is strictly above
 * MERGE_AFFINITY ([pickMerge] breaks ties). Input order never matters: lifts are sorted first.
 */
internal fun clusterLifts(
    lifts: Collection<Long>,
    days: List<String>,
    history: LiftHistory,
    tunables: LearnedTunables,
): List<List<Long>> {
    val ids = lifts.distinct().sorted()
    val n = ids.size
    val onDay = Array(n) { i -> BooleanArray(days.size) { d -> ids[i] in history.lifts(days[d]) } }
    val aff = Array(n) { DoubleArray(n) }
    for (i in 0 until n) for (j in i + 1 until n) {
        val a = affinity(ids[i], ids[j], days, history)
        aff[i][j] = a
        aff[j][i] = a
    }
    fun coverage(members: List<Int>): Int = days.indices.count { d -> members.any { onDay[it][d] } }

    val clusters = MutableList(n) { listOf(it) }
    while (clusters.size > 1) {
        val candidates = mutableListOf<MergeCandidate>()
        for (i in clusters.indices) for (j in i + 1 until clusters.size) {
            var sum = 0.0
            for (x in clusters[i]) for (y in clusters[j]) sum += aff[x][y]
            val mean = sum / (clusters[i].size * clusters[j].size)
            if (mean <= tunables.mergeAffinity + EPSILON) continue
            val union = clusters[i] + clusters[j]
            candidates += MergeCandidate(i, j, mean, coverage(union), union.map { ids[it] }.sorted())
        }
        val merge = pickMerge(candidates) ?: break
        clusters[merge.i] = clusters[merge.i] + clusters[merge.j]
        clusters.removeAt(merge.j)
    }
    return clusters.map { members -> members.map { ids[it] }.sorted() }.sortedWith(idListOrder)
}

/** The group test: at least GROUP_MIN_LIFTS lifts and GROUP_MIN_CO_DAYS days with two or more members. */
internal fun groupTest(
    members: List<Long>,
    days: List<String>,
    history: LiftHistory,
    tunables: LearnedTunables,
): LiftCluster? {
    if (members.size < tunables.groupMinLifts) return null
    val coDays = days.filter { date -> history.lifts(date).count { it in members } >= 2 }
    return if (coDays.size >= tunables.groupMinCoDays) LiftCluster(members, coDays) else null
}

/**
 * Held → everyday → clustering → group test. Everyday lifts are left out of clustering; when that
 * leaves fewer than two groups the engine reruns with every held lift clusterable and no lift is
 * everyday (the same six lifts every session are one group, not six solos).
 */
internal fun learnClusters(history: LiftHistory, asOf: Int, windows: LearnedWindows, tunables: LearnedTunables): LearnedClusters {
    val held = heldLifts(history, asOf, windows)
    val days = clusterWindowDays(history, asOf, windows)
    val everyday = everydayLifts(held, history, days, tunables)
    fun run(excluded: Set<Long>): List<LiftCluster> =
        clusterLifts(held.filterNot { it in excluded }, days, history, tunables)
            .mapNotNull { groupTest(it, days, history, tunables) }
    val withExclusion = run(everyday)
    if (everyday.isEmpty() || withExclusion.size >= 2) {
        return LearnedClusters(held, everyday, withExclusion)
    }
    return LearnedClusters(held, emptySet(), run(emptySet()))
}
