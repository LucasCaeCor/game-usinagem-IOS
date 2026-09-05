package br.com.usinagemmaster.game.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.Foundation.NSBundle

@OptIn(ExperimentalForeignApi::class)
actual object GameFeedback {
    private var ambience: AVAudioPlayer? = null
    private var effectPlayer: AVAudioPlayer? = null

    private fun load(name: String): AVAudioPlayer? {
        val url = NSBundle.mainBundle.URLForResource(name, withExtension = "wav") ?: return null
        return AVAudioPlayer(contentsOfURL = url, error = null).apply {
            prepareToPlay()
        }
    }

    actual fun setFactoryAmbience(enabled: Boolean) {
        if (!enabled) {
            ambience?.pause()
            return
        }
        val player = ambience ?: load("factory_ambient")?.also {
            it.numberOfLoops = -1
            it.volume = .17f
            ambience = it
        }
        if (player?.playing != true) player?.play()
    }

    actual fun play(effect: GameSoundEffect, enabled: Boolean) {
        if (!enabled) return
        val resource = when (effect) {
            GameSoundEffect.UI_CLICK -> "ui_click"
            GameSoundEffect.MACHINE_TICK -> "machine_tick"
            GameSoundEffect.MACHINE_START -> "machine_start"
            GameSoundEffect.WELD_SPARK -> "weld_spark"
            GameSoundEffect.QUALITY_PASS -> "quality_pass"
            GameSoundEffect.REWARD -> "reward_sting"
        }
        effectPlayer?.stop()
        effectPlayer = load(resource)?.apply {
            volume = when (effect) {
                GameSoundEffect.UI_CLICK -> .28f
                GameSoundEffect.WELD_SPARK -> .22f
                GameSoundEffect.MACHINE_TICK -> .18f
                else -> .40f
            }
            play()
        }
    }

    actual fun haptic(enabled: Boolean) {
        if (enabled) AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }
}
