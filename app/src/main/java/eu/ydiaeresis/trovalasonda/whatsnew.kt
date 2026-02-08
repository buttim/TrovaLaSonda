package eu.ydiaeresis.trovalasonda

import android.content.Context
import android.view.WindowManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.core.content.edit
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.osmdroid.views.MapView


fun whatsnew(context:Context,callback:(()->Unit)) {
    //TODO: segnare ultima versione ecc ecc
    val whatsnewTag="WHATSNEW"
    val prefs=context.getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE)

    if (prefs.getInt(whatsnewTag,0)%1000!=BuildConfig.VERSION_CODE%1000) {
        val wv=WebView(context)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, true)

        context.resources.openRawResource(R.raw.whatsnew).use { stream ->
            stream.bufferedReader().use { reader ->
                wv.loadData(reader.readText(), "text/html", null)
            }
        }
        MaterialAlertDialogBuilder(context,R.style.MaterialAlertDialog_rounded)
            .setTitle(context.getString(R.string.what_s_new))
            .setView(wv)
            .setPositiveButton(context.getString(R.string.close)) {_,_ ->
                callback()
            }
            .show()
        wv.requestLayout()
        prefs.edit {
            putInt(whatsnewTag,BuildConfig.VERSION_CODE%1000)
            commit()
        }
    }
    else callback()
}