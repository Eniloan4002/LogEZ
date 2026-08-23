package com.enil.logez.feature.workout.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.enil.logez.R
import com.enil.logez.core.domain.model.VolumeLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §9.7 — a platform side-effect component, injected into `WorkoutSessionService`
 * and never into a ViewModel (spine testing rule). Original, procedurally-generated short tones
 * in `res/raw` (§7.5 placeholder-media precedent: no Hevy assets). `USAGE_ASSISTANCE_SONIFICATION`
 * mixes over the user's own audio (gym music) without claiming audio focus.
 */
interface WorkoutAudioPlayer {
    fun playTimerSound(soundId: Int, volume: VolumeLevel)
    fun playSetCompleteSound(volume: VolumeLevel)
    fun playPrFanfare(volume: VolumeLevel)
    fun release()
}

@Singleton
class SoundPoolWorkoutAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkoutAudioPlayer {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(attributes)
        .build()

    private val timerSoundIds: Map<Int, Int> = mapOf(
        1 to soundPool.load(context, R.raw.timer_sound_1, 1),
        2 to soundPool.load(context, R.raw.timer_sound_2, 1),
        3 to soundPool.load(context, R.raw.timer_sound_3, 1),
        4 to soundPool.load(context, R.raw.timer_sound_4, 1),
        5 to soundPool.load(context, R.raw.timer_sound_5, 1),
    )
    private val setCompleteSoundId = soundPool.load(context, R.raw.set_complete, 1)
    private val prFanfareSoundId = soundPool.load(context, R.raw.pr_fanfare, 1)

    override fun playTimerSound(soundId: Int, volume: VolumeLevel) {
        val id = timerSoundIds[soundId] ?: timerSoundIds.getValue(1)
        play(id, volume)
    }

    override fun playSetCompleteSound(volume: VolumeLevel) = play(setCompleteSoundId, volume)

    override fun playPrFanfare(volume: VolumeLevel) = play(prFanfareSoundId, volume)

    private fun play(soundId: Int, volume: VolumeLevel) {
        val level = volume.toSoundPoolVolume()
        if (level <= 0f) return
        soundPool.play(soundId, level, level, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
    }

    override fun release() = soundPool.release()
}

/** §9.7: off/low/normal/high -> 0f/0.33f/0.66f/1f. */
private fun VolumeLevel.toSoundPoolVolume(): Float = when (this) {
    VolumeLevel.OFF -> 0f
    VolumeLevel.LOW -> 0.33f
    VolumeLevel.NORMAL -> 0.66f
    VolumeLevel.HIGH -> 1f
}
