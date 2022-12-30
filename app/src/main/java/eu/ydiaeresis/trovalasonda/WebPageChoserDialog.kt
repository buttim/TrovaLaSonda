package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.ydiaeresis.trovalasonda.databinding.WebpageChoserBinding
import java.util.*

class WebPageChoserDialog  : DialogFragment(), View.OnClickListener {
    private lateinit var binding: WebpageChoserBinding
    var sondeId:String?= null
    var sondeType:String?=null
    var lat:Double?=null
    var lon:Double?=null

    private fun getAprsId():String =
        when (sondeType) {
        "M20" -> {
            val leftAsInt=sondeId!!.substring(0,3).toInt()
            val right=sondeId!!.substring(sondeId!!.length-5)
            String.format("ME%02X%s",leftAsInt-49,right)
        }
        "M10" -> {
            val leftAsInt=sondeId!!.substring(0,1).toInt()
            val secondAsInt=sondeId!!.substring(1,3).toInt()
            val middle=sondeId!!.substring(3,4)
            val rightAsInt=sondeId!!.substring((4)).toInt()
            String.format("ME%1X%1X%s%04X",leftAsInt,secondAsInt,middle,rightAsInt-1808)
        }
        "DFM" -> "D"+sondeId!!.split('-').last().trimStart('0')//TODO: not tested
        else -> sondeId!!
        }

    private fun getSondehubId():String =
        when (sondeType) {
            "M10","M20" -> sondeId!!.substring(0,3)+"-"+sondeId!!.substring(3,4)+"-"+sondeId!!.substring(4)
            else -> sondeId!!
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { it ->
            val inflater = requireActivity().layoutInflater
            binding= WebpageChoserBinding.inflate(inflater).apply {
                tracker.setOnClickListener {
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://radiosondy.info/sonde.php?sondenumber=${getAprsId()}")
                        startActivity(this)
                    }
                    dialog?.cancel()
                }
                sondehub.setOnClickListener {
                    Intent(Intent.ACTION_VIEW).apply {
                        val url=String.format(Locale.US,
                            "https://tracker.sondehub.org/#!mt=Mapnik&mz=10&qm=0&mc=%f,%f&f=%s&q=%s",
                            lat,lon,getSondehubId(),getSondehubId())
                        data = Uri.parse(url)
                        startActivity(this)
                    }
                    dialog?.cancel()
                }
            }
            return MaterialAlertDialogBuilder(it, R.style.MaterialAlertDialog_rounded)
                .setView(binding.root)
                .setNegativeButton("Cancel") { _, _ -> dialog?.cancel() }
                .create()
          } ?: throw IllegalStateException("Activity cannot be null")
        }
        override fun onClick(v: View?) {}
}