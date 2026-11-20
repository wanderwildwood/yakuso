package com.wanderwildwood.yakuso.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one brew timer. What it is set to, and when it ends, are kept on the phone, so the exact
 * alarm's receiver, started cold by the system with the app long gone, knows as much as the
 * screen does. Parked's meter (okiba MeterAlarms), cut down to one timer: one exact alarm, let
 * through Doze, and the ring played by [RingService] in the foreground.
 */
object BrewTimer {
    private const val TAG = "yakuso"
    private const val PREFS = "brew"

    private val _brew = MutableStateFlow<Brew?>(null)

    /** The timer as it stands, for the screen; read from the phone on first use. */
    fun brew(context: Context): StateFlow<Brew?> {
        if (_brew.value == null) _brew.value = load(context)
        return _brew
    }

    fun current(context: Context): Brew = brew(context).value ?: default(context)

    /** Sets what will brew without starting it: from a preset, a herb's page, or − and +. */
    fun set(context: Context, b: Brew) {
        if (current(context).running) return
        save(context, b.copy(endAt = 0, finishedAt = 0))
        RingService.stop(context)
        Ringing.clear(context)
    }

    fun start(context: Context) {
        val b = current(context)
        save(context, b.copy(endAt = System.currentTimeMillis() + b.seconds * 1000L, finishedAt = 0))
        Ringing.clear(context)
        schedule(context)
    }

    /** Stops a running timer before it ends. Nothing rings. */
    fun cancel(context: Context) {
        save(context, current(context).copy(endAt = 0, finishedAt = 0))
        RingService.stop(context)
        Ringing.clear(context)
        schedule(context)
    }

    /** A finished brew put away: the timer goes back to what it was set to, ready again. */
    fun done(context: Context) {
        RingService.stop(context)
        Ringing.clear(context)
        save(context, current(context).copy(endAt = 0, finishedAt = 0))
    }

    /** The alarm went off (or the app opened after it should have): ring if it is time. */
    fun fire(context: Context) {
        val b = current(context)
        if (!b.running) return
        val now = System.currentTimeMillis()
        if (now < b.endAt - 500) {
            // Early (an inexact alarm, or a clock moved): set it again for the real end.
            schedule(context)
            return
        }
        save(context, b.copy(finishedAt = now))
        Log.i(TAG, "brew done at $now (due ${b.endAt})")
        Ringing.ring(context, b)
    }

    /**
     * Opening the app sets the alarm again, so one dropped by a force-stop, an update or exact
     * alarms taken back still rings (Parked, 204ffe5); one already due rings now.
     */
    fun resync(context: Context) {
        val b = current(context)
        if (b.running && System.currentTimeMillis() >= b.endAt) fire(context) else schedule(context)
    }

    /** Sets the one alarm for the end of a running brew, or clears it when none runs. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val b = current(context)
        if (!b.running) {
            am.cancel(pi)
            return
        }
        val at = maxOf(b.endAt, System.currentTimeMillis() + 500L)
        // Exact, and let through Doze: tea that rings five minutes late is stewed.
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Log.w(TAG, "no exact alarms allowed; inexact alarm set")
        }
        Log.i(TAG, "brew alarm set for $at")
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BrewReceiver::class.java).setAction(BrewReceiver.FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Black tea, until the person sets something else. */
    private fun default(context: Context): Brew {
        val black = BrewMath.BUILT_IN.first { it.key == BrewMath.BLACK }
        return Brew(label = Ringing.presetName(context, black), seconds = black.minSeconds, temp = black.temp)
    }

    private fun load(context: Context): Brew {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("seconds")) return default(context)
        val f = p.getInt("tempF", -1)
        val c = p.getInt("tempC", -1)
        return Brew(
            label = p.getString("label", null).orEmpty(),
            seconds = p.getInt("seconds", 240),
            temp = if (f >= 0 && c >= 0) Temp(f, c) else null,
            herbId = p.getString("herbId", null),
            endAt = p.getLong("endAt", 0),
            finishedAt = p.getLong("finishedAt", 0),
        )
    }

    private fun save(context: Context, b: Brew) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("label", b.label)
            .putInt("seconds", b.seconds)
            .putInt("tempF", b.temp?.f ?: -1)
            .putInt("tempC", b.temp?.c ?: -1)
            .putString("herbId", b.herbId)
            .putLong("endAt", b.endAt)
            .putLong("finishedAt", b.finishedAt)
            .commit()
        _brew.value = b
    }
}

/** The brew's alarm going off, and Stop pressed on its notification. */
class BrewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FIRE -> BrewTimer.fire(context)
            STOP -> Ringing.stop(context)
        }
    }

    companion object {
        const val FIRE = "com.wanderwildwood.yakuso.BREW"
        const val STOP = "com.wanderwildwood.yakuso.BREW_STOP"
    }
}

/**
 * An update of this app, and exact alarms being allowed again, both clear the alarm: each sets
 * it again from what is stored, and a brew whose end passed meanwhile rings now.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> BrewTimer.resync(context)
        }
    }
}
