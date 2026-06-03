package com.marrow.browser

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class MemoryMonitor(
    context: Context,
    private val onLevel: (Level) -> Unit
) {
    enum class Level { GREEN, YELLOW, RED }

    private val handler = Handler(Looper.getMainLooper())
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    companion object {
        private const val POLL_MS          = 3000L
        private const val YELLOW_THRESHOLD = 0.25f  // < 25% free RAM → yellow
        private const val RED_THRESHOLD    = 0.10f  // < 10% free RAM → red
    }

    private val poll = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() { handler.post(poll) }

    fun stop() { handler.removeCallbacks(poll) }

    private fun check() {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        // freeFraction: how much of total RAM is currently available
        val freeFraction = info.availMem.toFloat() / info.totalMem.toFloat()
        val level = when {
            freeFraction < RED_THRESHOLD    -> Level.RED
            freeFraction < YELLOW_THRESHOLD -> Level.YELLOW
            else                            -> Level.GREEN
        }
        onLevel(level)
    }
}

