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
        val id=sondeId!!.replace("-","")
        return when (sondeType) {
            "M20" -> {
                val leftAsInt=id.substring(0,3).toInt()
                val right=id.substring(sondeId!!.length-5)
                String.format("ME%02X%s",leftAsInt-49,right)
            }
            "M10" -> {
                val leftAsInt=id.substring(0,1).toInt()
                val secondAsInt=id.substring(1,3).toInt()
                val middle=id.substring(3,4)
                val rightAsInt=id.substring((4)).toInt()
                String.format("ME%1X%1X%s%04X",leftAsInt,secondAsInt,middle,rightAsInt-1808)
            }
            "DFM" -> "D"+id.split('-').last().trimStart('0')//TODO: not tested
            else -> sondeId!!
        }
    }

    private fun getSondehubId():String {
        val id=sondeId!!.replace("-","")

        return when (sondeType) {
            "M10" -> sondeId!!.substring(0,8)+sondeId!!.substring(9)
            "M20" -> id.substring(0,3)+"-"+id.substring(3,4)+"-"+id.substring(4)
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
                        launchPage(Uri.parse("https://radiosondy.info/sonde.php?sondenumber=${getAprsId()}"))
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
                        launchPage(Uri.parse(url))
                    }
                }
            }
            return MaterialAlertDialogBuilder(it, R.style.MaterialAlertDialog_rounded)
                .setView(binding.root)
                .setNegativeButton(R.string.CANCEL) { _, _ -> dialog?.cancel() }
                .create()
          } ?: throw IllegalStateException("Activity cannot be null")
        }
    override fun onClick(v: View?) {}
}