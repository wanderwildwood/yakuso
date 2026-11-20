package com.wanderwildwood.yakuso.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays the ring for a finished brew until Stop, or for [Ringing.RING_MS]: the phone's alarm
 * sound in a loop on the alarm stream, with the ringing notification held up as this service's
 * own. Then the sound ends and the quiet "ready" notification takes its place. Parked's
 * RingService (okiba), cut down to one sound and no buzz.
 *
 * During a call it rings quietly, at a tenth of its volume, so the call is not drowned.
 */
class RingService : Service() {

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val quieten = Runnable { quieten() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val b = BrewTimer.current(this)
        Ringing.channels(this)
        // Started with startForegroundService, the service must go to the foreground before
        // anything else, even when there turns out to be nothing to ring.
        startForeground(Ringing.ID, Ringing.loud(this, b), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (!b.finished) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        _ringing.value = true
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yakuso:ringing")
                .apply { acquire(Ringing.RING_MS + 10_000L) }
        }
        if (player == null) startSound()
        handler.removeCallbacks(quieten)
        handler.postDelayed(quieten, Ringing.RING_MS)
        return START_NOT_STICKY
    }

    private fun startSound() {
        val audio = getSystemService(AudioManager::class.java)
        val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { audio.requestAudioFocus(it) }
        player = candidates().firstNotNullOfOrNull { open(it, attrs) }
        val p = player
        if (p == null) {
            Log.e("yakuso", "no alarm sound could be played")
        } else {
            if (inCall) p.setVolume(0.1f, 0.1f)
            p.start()
            Log.i("yakuso", "ringing${if (inCall) " quietly, in a call" else ""}")
        }
    }

    /** The phone's alarm sound, then its default alarm, ringtone and notification sounds. */
    private fun candidates(): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    ).distinct()

    private fun open(uri: Uri, attrs: AudioAttributes): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@RingService, uri)
            isLooping = true
            setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
            setOnErrorListener { mp, _, _ ->
                Log.e("yakuso", "the alarm sound stopped with an error")
                if (player === mp) player = null
                runCatching { mp.release() }
                true
            }
            prepare()
        }
    } catch (e: Exception) {
        Log.w("yakuso", "cannot play $uri: ${e.message}")
        null
    }

    /** Nobody answered: the sound ends, and the quiet notification says the tea is ready. */
    private fun quieten() {
        val b = BrewTimer.current(this)
        stopSound()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (b.finished) Ringing.post(this, Ringing.READY_ID, Ringing.ready(this, b))
        stopSelf()
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun onDestroy() {
        _ringing.value = false
        handler.removeCallbacks(quieten)
        stopSound()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        super.onDestroy()
    }

    companion object {
        private val _ringing = MutableStateFlow(false)

        /** True while the sound plays: the timer's screen shows Stop. */
        val ringing: StateFlow<Boolean> = _ringing

        /** Starts the ring, or says it could not be started from where this was called. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, RingService::class.java))
            true
        } catch (e: Exception) {
            Log.w("yakuso", "ringing service not started: ${e.message}")
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingService::class.java))
        }
    }
}
