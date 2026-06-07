package com.marrow.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private var webViewWarmed = false
    private var animationDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val container = findViewById<LinearLayout>(
            resources.getIdentifier("splashIcon", "id", packageName)
        ).parent as View

        // Fade in the splash content
        container.animate()
            .alpha(1f)
            .setDuration(300)
            .setListener(null)
            .start()

        // Pre-warm WebView in background
        warmWebView()

        // Minimum display time — fade out after 1.2s minimum
        container.postDelayed({
            animationDone = true
            if (webViewWarmed) fadeOutAndLaunch(container)
        }, 1200)
    }

    private fun warmWebView() {
        val wv = WebView(applicationContext)
        wv.settings.javaScriptEnabled = true
        wv.loadUrl("about:blank")
        wv.post {
            webViewWarmed = true
            val container = (findViewById<View>(
                resources.getIdentifier("splashIcon", "id", packageName)
            ).parent as View)
            if (animationDone) fadeOutAndLaunch(container)
        }
    }

    private fun fadeOutAndLaunch(view: View) {
        view.animate()
            .alpha(0f)
            .setDuration(250)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            })
            .start()
    }
}
