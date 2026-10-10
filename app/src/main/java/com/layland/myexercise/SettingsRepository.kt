package com.layland.myexercise

import android.content.Context

interface SettingsRepository { fun load(): WorkoutConfig; fun save(config: WorkoutConfig) }

class PreferencesSettings(context: Context) : SettingsRepository {
    private val preferences = context.getSharedPreferences("workout_settings", Context.MODE_PRIVATE)
    private val resources = context.resources
    override fun load() = WorkoutConfig(
        mode = ExerciseMode.entries.firstOrNull { it.name == preferences.getString(MODE_KEY, null) } ?: ExerciseMode.STANDARD,
        workSeconds = preferences.getInt("work", WorkoutConfig.DEFAULT_WORK),
        restSeconds = preferences.getInt("rest", WorkoutConfig.DEFAULT_REST),
        sets = preferences.getInt("sets", WorkoutConfig.DEFAULT_SETS),
        countdownSeconds = preferences.getInt("countdown", WorkoutConfig.DEFAULT_COUNTDOWN),
        encourageEvery = preferences.getInt("encourage_every", WorkoutConfig.DEFAULT_ENCOURAGE),
        startPhrase = preferences.getString("start_phrase", null) ?: resources.getString(R.string.default_start),
        restPhrase = preferences.getString("rest_phrase", null) ?: resources.getString(R.string.default_rest),
        finishPhrase = preferences.getString("finish_phrase", null) ?: resources.getString(R.string.default_finish),
        encouragePhrase = preferences.getString("encourage_phrase", null) ?: resources.getString(R.string.default_encourage),
    )
    override fun save(config: WorkoutConfig) {
        require(config.valid())
        preferences.edit().putString(MODE_KEY, config.mode.name).putInt("work", config.workSeconds).putInt("rest", config.restSeconds)
            .putInt("sets", config.sets).putInt("countdown", config.countdownSeconds)
            .putInt("encourage_every", config.encourageEvery).putString("start_phrase", config.startPhrase)
            .putString("rest_phrase", config.restPhrase).putString("finish_phrase", config.finishPhrase)
            .putString("encourage_phrase", config.encouragePhrase).apply()
    }
    private companion object { const val MODE_KEY = "exercise_mode" }
}
