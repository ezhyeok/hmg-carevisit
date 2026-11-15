package dev.hmg.pleoskit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** Speech is interrupted rather than spoken over a call/navigation prompt. No automatic resume. */
class AudioFocusKit(context: Context) : SpeechFocus {
    private val audio=context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val lock=Any()
    private var request: AudioFocusRequest?=null
    private var epoch=0L
    override fun acquire(onLoss: ()->Unit): Boolean = synchronized(lock) {
        release()
        val turn=++epoch
        val r=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                synchronized(lock) { if(turn==epoch && change in setOf(AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {onLoss();release()} }
            }.build()
        request=r
        (audio.requestAudioFocus(r)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED).also {if(!it) release()}
    }
    override fun release() = synchronized(lock) {
        epoch++
        request?.let {audio.abandonAudioFocusRequest(it)}
        request=null
    }
}
