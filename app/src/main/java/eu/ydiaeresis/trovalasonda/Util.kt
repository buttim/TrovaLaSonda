package eu.ydiaeresis.trovalasonda

import android.content.Context
import android.os.CountDownTimer
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import java.util.concurrent.TimeUnit

fun timedWarning(
    ctx: Context,
    title: String,
    message: String,
    timeout: Int,
    callback: (() -> Unit)? = null
) {
    val dlg: AlertDialog = MaterialAlertDialogBuilder(
        ctx,
        R.style.MaterialAlertDialog_rounded
    )
        .setIconAttribute(android.R.attr.alertDialogIcon)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .create()
    dlg.setOnShowListener {
        val defaultButton = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        val positiveButtonText = defaultButton.text
        object : CountDownTimer(timeout * 1000L, 100) {
            override fun onTick(p0: Long) {
                defaultButton.text = String.format(
                    Locale.getDefault(), "%s (%d)",
                    positiveButtonText,
                    TimeUnit.MILLISECONDS.toSeconds(p0) + 1 //add one so it never displays zero
                )
            }

            override fun onFinish() {
                if (dlg.isShowing)
                    defaultButton.performClick()
                if (callback != null)
                    callback()
            }
        }
            .start()
    }
    dlg.show()
}
