package eu.ydiaeresis.trovalasonda

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import eu.ydiaeresis.trovalasonda.databinding.SondehubReportBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.isVisible


class SondehubReport:DialogFragment(),View.OnClickListener {
    private lateinit var binding:SondehubReportBinding
    var sondeId:String?=null
    var lat:Double?=null
    var lon:Double?=null
    var alt:Double?=null

    override fun onCreateView(
        inflater:LayoutInflater,
        container:ViewGroup?,
        savedInstanceState:Bundle?,
    ):View? {
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.setOnShowListener(object:DialogInterface.OnShowListener {
            override fun onShow(dialog:DialogInterface?) {
                val btnSendReport=
                    (dialog as androidx.appcompat.app.AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                btnSendReport.setOnClickListener(object:View.OnClickListener {
                    override fun onClick(v:View?) {
                        with(binding) {
                            if (user.text.toString().isEmpty()) {
                                Snackbar.make(binding.root,
                                    getString(R.string.user_name_must_be_specified),
                                    Snackbar.LENGTH_LONG).show()
                                user.requestFocus()
                                return
                            }
                            val prefs=context?.getSharedPreferences(BuildConfig.APPLICATION_ID,
                                MODE_PRIVATE)
                            prefs?.edit {
                                putString(SONDEHUB_REPORT_USER,user.text.toString())
                                commit()
                            }
                            try {
                                lat=latitude.text.toString().toDouble()
                            } catch (_:NumberFormatException) {
                                latitude.requestFocus()
                                Snackbar.make(binding.root,
                                    getString(R.string.INVALID_LATITUDE),
                                    Snackbar.LENGTH_LONG).show()
                                return
                            }
                            try {
                                lon=longitude.text.toString().toDouble()
                            } catch (_:NumberFormatException) {
                                longitude.requestFocus()
                                Snackbar.make(binding.root,
                                    getString(R.string.INVALID_LONGITUDE),
                                    Snackbar.LENGTH_LONG).show()
                                return
                            }
                            try {
                                alt=altitude.text.toString().toDouble()
                            } catch (_:NumberFormatException) {
                                Snackbar.make(binding.root,
                                    getString(R.string.INVALID_ALTITUDE),
                                    Snackbar.LENGTH_LONG).show()
                                altitude.requestFocus()
                                return
                            }
                            if (description.text.toString().isEmpty()) {
                                Snackbar.make(binding.root,
                                    getString(R.string.please_add_a_description),
                                    Snackbar.LENGTH_LONG).show()
                                description.requestFocus()
                                return
                            }
                            waitProgress.visibility=View.VISIBLE
                        }
                        btnSendReport.isEnabled=false
                        var result:String?=null
                        CoroutineScope(Dispatchers.IO).launch {
                            with(binding) {
                                result=recovered(requireContext(),
                                    user.text.toString(),
                                    sondeId!!,
                                    lat!!,
                                    lon!!,
                                    alt!!,
                                    description.text.toString())
                            }
                        }.invokeOnCompletion {
                            try {
                                if (result==null) {
                                    Snackbar.make(binding.root,
                                        getString(R.string.report_sent_successfully),Snackbar.LENGTH_LONG)
                                        .addCallback(object:Snackbar.Callback() {
                                            override fun onDismissed(
                                                transientBottomBar:Snackbar?,
                                                event:Int,
                                            ) {
                                                super.onDismissed(transientBottomBar,event)
                                                dialog.dismiss()
                                            }
                                        }).show()
                                }
                                else {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        binding.waitProgress.visibility=View.GONE
                                        MaterialAlertDialogBuilder(this@SondehubReport.requireContext(),
                                            R.style.MaterialAlertDialog_rounded).setIconAttribute(
                                                android.R.attr.alertDialogIcon)
                                            .setTitle(getString(R.string.failed_try_again))
                                            .setMessage(getString(R.string.reporting_result,result))
                                            .setPositiveButton(R.string.YES) {_,_ ->
                                                btnSendReport.isEnabled=true
                                            }.setNegativeButton("No") {_,_ ->
                                                dialog.dismiss()
                                            }.show()
                                    },0)
                                }
                            } catch (ex:Exception) {
                                Log.w(FullscreenActivity.TAG,
                                    "Exception while dismissing dialog: $ex")
                            }
                        }
                    }
                })
            }
        })
        return super.onCreateView(inflater,container,savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
        return activity?.let {
            val inflater=requireActivity().layoutInflater
            binding=SondehubReportBinding.inflate(inflater).apply {
                val prefs=context?.getSharedPreferences(BuildConfig.APPLICATION_ID,MODE_PRIVATE)
                val savedUser=prefs?.getString(SONDEHUB_REPORT_USER,"")
                user.setText(savedUser)
                latitude.setText(lat.toString())
                longitude.setText(lon.toString())
                altitude.setText(alt.toString())
                description.requestFocus()
                showCoords.setOnClickListener {
                    if (coordsLayout.isVisible) {
                        showCoords.text=resources.getString(R.string.coord_closed)
                        coordsLayout.visibility=View.GONE
                        description.requestFocus()
                    }
                    else {
                        showCoords.text=resources.getString(R.string.coord_open)
                        coordsLayout.visibility=View.VISIBLE
                        latitude.requestFocus()
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    val inputMethodManager=
                        activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showSoftInput(description,InputMethodManager.SHOW_IMPLICIT)
                },100)
            }
            /*return*/ MaterialAlertDialogBuilder(it,R.style.MaterialAlertDialog_rounded).setView(
                binding.root).setNegativeButton(R.string.CANCEL) {_,_ -> dialog?.cancel()}
                .setPositiveButton(getString(R.string.SEND_REPORT)) {_,_ ->

                }.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onClick(v:View?) {}

    companion object {
        private const val SONDEHUB_REPORT_USER="SONDEHUB_REPORT_USER"
    }
}