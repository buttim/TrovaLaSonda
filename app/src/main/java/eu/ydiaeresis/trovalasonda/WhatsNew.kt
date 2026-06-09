package eu.ydiaeresis.trovalasonda

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit // Core KTX extension import
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun whatsNew(context: Context, callback: (() -> Unit)) {
    val whatsNewTag = "whatsNew"
    val prefs =
        context.getSharedPreferences(BuildConfig.APPLICATION_ID, AppCompatActivity.MODE_PRIVATE)

    if (prefs.getInt(whatsNewTag, 0) % 1000 != BuildConfig.VERSION_CODE % 1000) {

        // 1. Create a native FrameLayout wrapper.
        // We initialize it with WRAP_CONTENT so it sits naturally inside the Material layout.
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 2. Instantiate the WebView with MATCH_PARENT.
        // It takes up exactly what the container dictates, preventing it from resizing itself.
        val wv = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // THE FIX: Wait for the HTML canvas to finish its calculation layout pass.
                    // The exact millisecond the text is drawn, grab its final pixel footprint
                    // and freeze the parent container's height explicitly.
                    view?.postDelayed({
                        val contentHeight =
                            (view.contentHeight * context.resources.displayMetrics.density).toInt()
                        if (contentHeight > 0) {
                            container.layoutParams = container.layoutParams.apply {
                                height = contentHeight
                            }
                            container.requestLayout() // Lock it in!
                        }
                    }, 100) // Small 100ms delay ensures the engine's text wrapping is fully settled
                }
            }
        }

        // Retain your clean dark mode configurations
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, true)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            WebSettingsCompat.setForceDarkStrategy(
                wv.settings,
                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
            )
        }

        // Load your HTML resource with your CSS file structure fully intact
        context.resources.openRawResource(R.raw.whatsnew).use { stream ->
            stream.bufferedReader().use { reader ->
                wv.loadDataWithBaseURL(
                    "file:///android_asset/",
                    reader.readText(),
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }

        // Assemble the layout frame structure
        container.addView(wv)

        // 3. Render the dialog naturally. It will fit snuggly around your content
        // and cannot vibrate because the parent height parameters are completely locked down.
        MaterialAlertDialogBuilder(context, R.style.MaterialAlertDialog_rounded)
            .setTitle(context.getString(R.string.what_s_new))
            .setView(container) // Pass the rigid container structure
            .setPositiveButton(context.getString(R.string.close)) { _, _ ->
                callback()
            }
            .show()

        prefs.edit {
            putInt(whatsNewTag, BuildConfig.VERSION_CODE % 1000)
            commit()
        }
    } else callback()
}