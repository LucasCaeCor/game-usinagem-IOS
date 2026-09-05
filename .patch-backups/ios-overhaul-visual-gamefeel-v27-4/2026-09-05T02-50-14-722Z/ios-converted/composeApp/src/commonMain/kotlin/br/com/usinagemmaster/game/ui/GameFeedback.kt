package br.com.usinagemmaster.game.ui

enum class GameSoundEffect {
    UI_CLICK,
    MACHINE_TICK,
    MACHINE_START,
    WELD_SPARK,
    QUALITY_PASS,
    REWARD,
}

expect object GameFeedback {
    fun setFactoryAmbience(enabled: Boolean)
    fun play(effect: GameSoundEffect, enabled: Boolean = true)
    fun haptic(enabled: Boolean = true)
}
