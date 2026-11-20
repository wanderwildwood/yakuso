package com.wanderwildwood.yakuso.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wanderwildwood.yakuso.MainActivity
import com.wanderwildwood.yakuso.R

/**
 * How a finished brew rings: [RingService] plays the phone's alarm sound in a loop on the alarm
 * volume (so a phone set to silent still rings) for [RING_MS], holding up a notification with
 * Stop. Nobody answering, the sound ends and a quiet notification stays that says the tea is
 * ready. Every channel asks for the notification light (LightChannel), so a phone face down
 * with its screen off still shows that something is waiting. Parked's ring (okiba Ringing), cut
 * down: no ringing screen over the lock screen, and no missed-alarm wording.
 */
object Ringing {
    /** The service's notification: the service makes the sound, so the channel has none. */
    const val RINGING = "brew_ringing"

    /** Kept for a ring without the service: the notification makes the sound itself. */
    private const val RING = "brew_ring"

    /** What stays after the sound: the tea is ready. */
    private const val READY = "brew_ready"

    const val ID = 1
    const val READY_ID = 2
    const val RING_MS = 60_000L

    /** Opens the app on the timer. */
    const val EXTRA_SCREEN = "screen"
    const val SCREEN_TIMER = "timer"

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarm = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sound = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        LightChannel.ensure(
            nm,
            null,
            NotificationChannel(RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_ringing_about)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        LightChannel.ensure(
            nm,
            null,
            NotificationChannel(RING, context.getString(R.string.channel_ring_fallback), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_ring_fallback_about)
                setSound(sound, alarm)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        LightChannel.ensure(
            nm,
            null,
            NotificationChannel(READY, context.getString(R.string.channel_ready), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_ready_about)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /** The brew [b] is done: ring. */
    fun ring(context: Context, b: Brew) {
        channels(context)
        context.getSystemService(NotificationManager::class.java).cancel(READY_ID)
        if (!RingService.start(context)) {
            // Android would not let the service start from here: the notification rings instead,
            // over and over until it is answered.
            post(context, ID, loud(context, b, RING).apply { flags = flags or Notification.FLAG_INSISTENT })
        }
    }

    /** Stop pressed: the sound ends and the notifications go. The timer stays finished. */
    fun stop(context: Context) {
        RingService.stop(context)
        clear(context)
    }

    fun clear(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ID)
        nm.cancel(READY_ID)
    }

    fun title(context: Context): String = context.getString(R.string.ring_title)

    fun text(context: Context, b: Brew): String =
        context.getString(R.string.ring_text, b.label.ifBlank { context.getString(R.string.timer_title) }, BrewMath.minutes(b.seconds))

    /** The ringing notification: it stays until Stop, and a tap on it opens the timer. */
    fun loud(context: Context, b: Brew, channel: String = RINGING): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title(context))
            .setContentText(text(context, b))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setContentIntent(open(context))
            .addAction(0, context.getString(R.string.ring_stop), stopIntent(context))
            .build()

    /** What stays after a ring nobody answered. */
    fun ready(context: Context, b: Brew): Notification =
        NotificationCompat.Builder(context, READY)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title(context))
            .setContentText(text(context, b))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(b.finishedAt)
            .setContentIntent(open(context))
            .build()

    fun post(context: Context, id: Int, n: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            Log.w("yakuso", "brew notification not posted: ${e.message}")
        }
    }

    /** A built-in preset's name from strings.xml, or the person's own. */
    fun presetName(context: Context, p: Preset): String = p.ownName ?: context.getString(
        when (p.key) {
            BrewMath.GREEN -> R.string.preset_green
            BrewMath.BLACK -> R.string.preset_black
            BrewMath.HERBAL -> R.string.preset_herbal
            else -> R.string.preset_decoction
        },
    )

    private fun stopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, BrewReceiver::class.java).setAction(BrewReceiver.STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SCREEN, SCREEN_TIMER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
