package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import eu.ydiaeresis.trovalasonda.databinding.SondetypeBinding
import eu.ydiaeresis.trovalasonda.databinding.WebpageChoserBinding
import java.util.*

class WebPageChoserDialog  : DialogFragment(), View.OnClickListener {
    private lateinit var binding: WebpageChoserBinding
    var sondeId:String?= null
    var lat:Double?=null
    var lon:Double?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { it ->
            val inflater = requireActivity().layoutInflater
            binding= WebpageChoserBinding.inflate(inflater).apply {
                tracker.setOnClickListener {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://radiosondy.info/sonde.php?sondenumber=$sondeId")
                        startActivity(this)
                    }
                    dialog?.cancel()
                }
                sondehub.setOnClickListener {
                    Intent(Intent.ACTION_VIEW).apply {
                        val url=String.format(Locale.US,
                            "https://tracker.sondehub.org/#!mt=Mapnik&mz=10&qm=3h&mc=%f,%f&f=%s",
                            lat,lon,sondeId)
                        data = Uri.parse(url)
                        startActivity(this)
                    }
                    dialog?.cancel()
                }
            }
            return AlertDialog.Builder(it)
                .setView(binding.root)
                .setNegativeButton("Cancel") { _, _ -> dialog?.cancel() }
                .create()
          } ?: throw IllegalStateException("Activity cannot be null")
        }
        override fun onClick(v: View?) {}
}