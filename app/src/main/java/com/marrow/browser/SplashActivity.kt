package com.marrow.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        container = findViewById(R.id.splashContainer)

        // Fade in
        container.alpha = 0f
        container.animate().alpha(1f).setDuration(300).start()

        // Pre-warm WebView in background — fire and forget
        try {
            val wv = WebView(applicationContext)
            wv.settings.javaScriptEnabled = true
            wv.loadUrl("about:blank")
        } catch (_: Exception) {}

        // Launch after fixed delay — don't wait for WebView
        container.postDelayed({ launch() }, 1200)
    }

    private fun launch() {
        container.animate()
            .alpha(0f)
            .setDuration(250)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            })
            .start()
    }
}
