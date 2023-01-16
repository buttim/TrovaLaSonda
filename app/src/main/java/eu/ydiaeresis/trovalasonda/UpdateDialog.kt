package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.ydiaeresis.trovalasonda.databinding.UpdateDialogBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.io.File

class UpdateDialog  : DialogFragment(), View.OnClickListener {
    private lateinit var binding:UpdateDialogBinding
    lateinit var fullscreenActivity:FullscreenActivity
    lateinit var mutex:Mutex

    override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
        return activity?.let {it ->
            val inflater=requireActivity().layoutInflater
            binding=UpdateDialogBinding.inflate(inflater).apply {}
            val dlg=MaterialAlertDialogBuilder(it, R.style.MaterialAlertDialog_rounded)
                .setView(binding.root)
                .setTitle("Firmware update available")
                .setMessage("A new version of the firmware is available for this receiver.\nDo you want to update the receiver now?")
                .setPositiveButton("Update now") { _, _ ->  }
                .setNegativeButton("Cancel") { _, _ -> dialog?.cancel() }
                .create()
            dlg.setCanceledOnTouchOutside(false)
            dlg
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onStart() {
        super.onStart()
        val dlg=dialog as AlertDialog
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setOnClickListener {
                this.isEnabled=false
                dlg.apply {
                    setTitle("Downloading firmware")
                    setMessage("The firmware will be loaded on the receiver once the download is complete")
                }
                val file=File.createTempFile("firmware",".bin",context.cacheDir).apply {
                    deleteOnExit()
                }
                fullscreenActivity.startOta()
                CoroutineScope(Dispatchers.IO).launch {
                    FirmwareUpdater().getUpdate(file).collect {
                        withContext(Dispatchers.Main) {
                            when (it) {
                                is DownloadStatus.Progress ->
                                    binding.progressBar.progress=it.progress
                                is DownloadStatus.Error -> dlg.apply {
                                    setTitle("Firmware download failed")
                                    setMessage(it.message)
                                    fullscreenActivity.stopOta()
                                }
                                is DownloadStatus.Success -> {
                                    dlg.setTitle("Updating firmware")
                                    dlg.setMessage("The firmware was downloaded. Now updating the radio")
                                    binding.progressBar.progress=0
                                    CoroutineScope(Dispatchers.IO).launch {
                                        FirmwareUpdater().update(fullscreenActivity,mutex,file).collect {
                                            withContext(Dispatchers.Main) {
                                                when (it) {
                                                    is DownloadStatus.Progress ->
                                                        binding.progressBar.progress=it.progress
                                                    is DownloadStatus.Error -> dlg.apply {
                                                        setTitle("Firmware update failed")
                                                        setMessage(it.message)
                                                        fullscreenActivity.stopOta()
                                                    }
                                                    is DownloadStatus.Success -> dlg.apply {
                                                        setTitle("Firmware update finished")
                                                        setMessage("Wait for the radio to reboot and reconnect")
                                                        getButton(AlertDialog.BUTTON_POSITIVE).isVisible = false
                                                        getButton(AlertDialog.BUTTON_NEGATIVE).text="close"
                                                        invalidate()
                                                        fullscreenActivity.stopOta()
                                                    }
                                                    is DownloadStatus.NoContentLength -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                                is DownloadStatus.NoContentLength ->
                                    binding.progressBar.isIndeterminate=true
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onClick(v: View?) {}
}