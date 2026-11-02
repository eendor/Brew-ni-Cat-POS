package com.example.cattasticpos.ui.adaptive

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.example.cattasticpos.R
import com.example.cattasticpos.data.local.FeedbackPreferences

enum class PosSound {
    Tap,
    Select,
    Confirm,
    Success,
    Error,
    AddToCart,
    Checkout,
    NewOrder
}

data class FeedbackEvent(
    val haptic: BionicHaptic,
    val sound: PosSound? = null
)

class PosFeedbackController(
    context: Context,
    private val preferences: FeedbackPreferences
) {
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<PosSound, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
        soundIds[PosSound.Tap] = soundPool.load(context, R.raw.pos_tap, 1)
        soundIds[PosSound.Select] = soundPool.load(context, R.raw.pos_select, 1)
        soundIds[PosSound.Confirm] = soundPool.load(context, R.raw.pos_confirm, 1)
        soundIds[PosSound.Success] = soundPool.load(context, R.raw.pos_success, 1)
        soundIds[PosSound.Error] = soundPool.load(context, R.raw.pos_error, 1)
        soundIds[PosSound.AddToCart] = soundPool.load(context, R.raw.pos_add_cart, 1)
        soundIds[PosSound.Checkout] = soundPool.load(context, R.raw.pos_checkout, 1)
        soundIds[PosSound.NewOrder] = soundPool.load(context, R.raw.pos_new_order, 1)
    }

    fun perform(
        event: FeedbackEvent,
        performHaptic: (BionicHaptic) -> Unit
    ) {
        if (preferences.hapticsEnabled) {
            performHaptic(event.haptic)
        }
        if (preferences.soundsEnabled) {
            event.sound?.let { sound ->
                soundIds[sound]?.let { id ->
                    // a new online order has to be heard across the counter; taps stay quiet
                    val volume = if (sound == PosSound.NewOrder) 0.9f else 0.35f
                    soundPool.play(id, volume, volume, 1, 0, 1f)
                }
            }
        }
    }

    fun release() {
        soundPool.release()
    }
}

val LocalPosFeedback = compositionLocalOf<(FeedbackEvent) -> Unit> {
    { /* no-op */ }
}

@Composable
fun ProvidePosFeedback(
    preferences: FeedbackPreferences,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val performHaptic: (BionicHaptic) -> Unit = { type ->
        when (type) {
            BionicHaptic.Light -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            BionicHaptic.Selection -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            BionicHaptic.Confirm -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            BionicHaptic.Snap -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            BionicHaptic.Success -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            BionicHaptic.Error -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            BionicHaptic.Add -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        Unit
    }
    val controller = remember(context) {
        PosFeedbackController(context, preferences)
    }
    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    val performFeedback = remember(controller, performHaptic) {
        { event: FeedbackEvent -> controller.perform(event, performHaptic) }
    }
    CompositionLocalProvider(LocalPosFeedback provides performFeedback, content = content)
}

@Composable
fun rememberPosFeedback(): (FeedbackEvent) -> Unit = LocalPosFeedback.current

/** @deprecated Use [rememberPosFeedback] with [FeedbackEvent]. */
@Composable
fun rememberBionicHaptic(): (BionicHaptic) -> Unit {
    val performFeedback = rememberPosFeedback()
    return remember(performFeedback) {
        { type -> performFeedback(FeedbackEvent(type)) }
    }
}
