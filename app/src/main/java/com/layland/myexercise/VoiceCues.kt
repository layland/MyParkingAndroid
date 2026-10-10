package com.layland.myexercise

sealed interface VoiceCue {
    data class Phrase(val text: String) : VoiceCue
    data class Count(val number: Int) : VoiceCue
    data class Remaining(val seconds: Int) : VoiceCue
}

/** Voice policy is independent of Android and retains the final count at a phase boundary. */
internal fun voiceCues(config: WorkoutConfig, before: WorkoutState, after: WorkoutState): List<VoiceCue> {
    if (before.paused || before.phase == Phase.READY || before.phase == Phase.DONE) return emptyList()
    val transitioned = before.phase != after.phase || before.set != after.set
    val phrase = when (after.phase) {
        Phase.WORK -> config.startPhrase
        Phase.REST -> config.restPhrase
        Phase.DONE -> config.finishPhrase
        Phase.READY -> null
    }
    if (config.mode == ExerciseMode.BREATHING) {
        val duration = if (before.phase == Phase.WORK) config.workSeconds else config.restSeconds
        return buildList {
            add(VoiceCue.Count(duration - before.remaining + 1))
            if (transitioned && phrase != null) add(VoiceCue.Phrase(phrase))
        }
    }
    return when {
        transitioned && phrase != null -> listOf(VoiceCue.Phrase(phrase))
        after.remaining in 1..config.countdownSeconds -> listOf(VoiceCue.Remaining(after.remaining))
        after.phase == Phase.WORK && config.encourageEvery > 0 &&
            (config.workSeconds - after.remaining) % config.encourageEvery == 0 -> listOf(VoiceCue.Phrase(config.encouragePhrase))
        else -> emptyList()
    }
}

/** Spoken words are injected from Android resources rather than hardcoded in the timer. */
internal class KoreanCountWords(
    private val ones: List<String>,
    private val tens: List<String>,
    private val prefixes: List<String>,
    private val hundred: String,
    private val thousand: String,
) {
    fun format(number: Int): String {
        require(number in 1..WorkoutConfig.MAX_SECONDS)
        val thousands = number / THOUSAND
        val hundreds = number % THOUSAND / HUNDRED
        val tail = number % HUNDRED
        return buildString {
            if (thousands > 0) append(prefixes[thousands]).append(thousand)
            if (hundreds > 0) append(prefixes[hundreds]).append(hundred)
            append(tens[tail / TEN]).append(ones[tail % TEN])
        }
    }
    companion object {
        private const val TEN = 10
        private const val HUNDRED = 100
        private const val THOUSAND = 1000
    }
}

/** Korean ordinal determiners, supplied by resources, for the supported set range. */
internal class KoreanOrdinalWords(
    private val first: String,
    private val ones: List<String>,
    private val tens: List<String>,
    private val twenty: String,
    private val hundred: String,
) {
    fun format(number: Int): String {
        require(number in 1..WorkoutConfig.MAX_SETS)
        return when (number) {
            1 -> first
            HUNDRED -> hundred
            TWENTY -> twenty
            else -> tens[number / TEN] + ones[number % TEN]
        }
    }
    private companion object {
        const val TEN = 10
        const val TWENTY = 20
        const val HUNDRED = 100
    }
}
