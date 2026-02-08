package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.ydiaeresis.trovalasonda.databinding.WebpageChoserBinding
import java.util.*
import androidx.core.net.toUri

class WebPageChooserDialog : DialogFragment(), View.OnClickListener {
    private lateinit var binding:WebpageChoserBinding
    private var sondeId:String?=null
    private var sondeType:String?=null
    private var lat:Double?=null
    private var lon:Double?=null
    private var alt:Double?=null
    private var isNotification=false
    private var supportFragmentManager:FragmentManager?=null

    private fun getAprsId():String {
        return when (sondeType) {
            "M20" -> {
                val tmp=sondeId!![2].code-48+
                        10*(sondeId!![1].code-48)-1+
                        12*(sondeId!![0].code-48)
                val data18=tmp or (((sondeId!![4].code-48-1) and 1) shl 7)
                String.format("ME%02X%s",data18,sondeId!!.substring(6))
            }
            "M10" -> {
                val data95=(sondeId!![0].code-48)*16+
                        10*(sondeId!![1].code-48)+
                        sondeId!![2].code-48
                val data96and7=sondeId!!.takeLast(4).toInt()+
                        ((sondeId!![6].code-48) shl 13)
                String.format("ME%2X%s%04X",data95,sondeId!![4],data96and7)
            }
            "DFM" -> "D"+sondeId!!.split('-').last().trimStart('0')//TODO: not tested
            else -> sondeId!!
        }
    }

    private fun getSondehubId():String {
        val id=sondeId!!.replace("-","")

        return when (sondeType) {
            "M10" -> sondeId!!.substring(0,8)+sondeId!!.substring(9)
            "M20" -> id.take(3)+"-"+id[3]+"-"+id.substring(4)
            else -> sondeId!!
        }
    }

    private fun launchPage(uri:Uri) {
        Intent(Intent.ACTION_VIEW).apply {
            data = uri
            startActivity(this)
        }
    }

    private fun showRadiosondyReport() {
        val intent=Intent(activity,ReportActivity::class.java)
        intent.putExtras(Bundle().apply {
            putString("sondeId",sondeId)
            putDouble("lat",lat!!)
            putDouble("lng",lon!!)
        })
        startActivity(intent)    }

    fun showForRecovery(supportFragmentManager:FragmentManager,sondeId:String,lat:Double,lon:Double,alt:Double) {
        this.sondeId=sondeId
        this.lat=lat
        this.lon=lon
        this.alt=alt
        this.supportFragmentManager=supportFragmentManager
        isNotification=true
        show(supportFragmentManager,"")
    }

    fun showForInfo(supportFragmentManager:FragmentManager,sondeId:String,lat:Double,lon:Double) {
        this.sondeId=sondeId
        this.lat=lat
        this.lon=lon
        show(supportFragmentManager,"")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val inflater = requireActivity().layoutInflater
            binding= WebpageChoserBinding.inflate(inflater).apply {
                if (isNotification)
                    title.text=getString(R.string.NOTIFY_RECOVERY)
                tracker.setOnClickListener {
                    if (isNotification)
                        showRadiosondyReport()
                    else
                        launchPage("https://radiosondy.info/sonde.php?sondenumber=${getAprsId()}".toUri())
                    dialog?.cancel()
                }
                sondehub.setOnClickListener {
                    dialog?.cancel()
                    if (isNotification) {
                        val dlg=SondehubReport()
                        dlg.sondeId=sondeId
                        dlg.lat=lat
                        dlg.lon=lon
                        dlg.alt=alt
                        dlg.show(supportFragmentManager!!,"")
                    }
                    else {
                        val url=String.format(Locale.US,
                            "https://tracker.sondehub.org/#!mt=Mapnik&mz=10&qm=0&mc=%f,%f&f=%s&q=%s",
                            lat,
                            lon,
                            getSondehubId(),
                            getSondehubId())
                        launchPage(url.toUri())
                    }
                }
            }
            /*return*/ MaterialAlertDialogBuilder(it, R.style.MaterialAlertDialog_rounded)
                .setView(binding.root)
                .setNegativeButton(R.string.CANCEL) { _, _ -> dialog?.cancel() }
                .create()
          } ?: throw IllegalStateException("Activity cannot be null")
        }
    override fun onClick(v: View?) {}
}