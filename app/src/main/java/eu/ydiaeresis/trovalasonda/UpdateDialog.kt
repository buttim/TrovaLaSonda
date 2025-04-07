package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.ydiaeresis.trovalasonda.databinding.UpdateDialogBinding
import kotlinx.coroutines.*
import java.io.File

class UpdateDialog(private val receiver:Receiver,
                   private val versionInfo:VersionInfo)  : DialogFragment(), View.OnClickListener {
    private lateinit var binding:UpdateDialogBinding
    private var canceled=false

    override fun onCreateDialog(savedInstanceState:Bundle?):Dialog {
        return activity?.let {
            val inflater=requireActivity().layoutInflater
            binding=UpdateDialogBinding.inflate(inflater).apply {}
            val dlg=MaterialAlertDialogBuilder(it, R.style.MaterialAlertDialog_rounded)
                .setView(binding.root)
                .setTitle(R.string.FIRMWARE_UPDATE_AVAILABLE)
                .setMessage(
                    if (versionInfo.info==null)
                        context?.getString(R.string.A_NEW_VERSION_OF_THE_FIRMWARE)
                    else
                        "v. ${versionInfo.version}\n${versionInfo.info}")
                .setPositiveButton(R.string.UPDATE_NOW) { _, _ ->  }
                .setNegativeButton(R.string.CANCEL) { _, _ -> dialog?.cancel() }
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
                    setTitle(R.string.DOWNLOADING_FIRMWARE)
                    setMessage(context.getString(R.string.THE_FIRMWARE_WILL_BE_LOADED))
                }
                val file=File.createTempFile("firmware",".bin",context.cacheDir).apply {
                    deleteOnExit()
                }
                CoroutineScope(Dispatchers.IO).launch {
                    FirmwareUpdater().getUpdate(versionInfo.file!!,file,receiver.getOtaChunkSize()).collect {
                        withContext(Dispatchers.Main) {
                            when (it) {
                                is DownloadStatus.Progress ->
                                    binding.progressBar.progress=it.progress
                                is DownloadStatus.Error -> dlg.apply {
                                    setTitle(R.string.FIRMWARE_DOWNLOAD_FAILED)
                                    setMessage(it.message)
                                    receiver.stopOTA()
                                }
                                is DownloadStatus.Success -> {
                                    dlg.setTitle(R.string.UPDATING_FIRMWARE)
                                    dlg.setMessage(context.getString(R.string.THE_FIRMWARE_HAS_BEEN_DOWNLOADED))
                                    binding.progressBar.progress=0
                                    CoroutineScope(Dispatchers.IO).launch {
                                        FirmwareUpdater().update(receiver,file).collect {
                                            withContext(Dispatchers.Main) {
                                                when (it) {
                                                    is DownloadStatus.Progress -> {
                                                        if (canceled) cancel()
                                                        binding.progressBar.progress=it.progress
                                                    }
                                                    is DownloadStatus.Error -> dlg.apply {
                                                        setTitle(R.string.FIRMWARE_DOWNLOAD_FAILED)
                                                        setMessage(it.message)
                                                        receiver.stopOTA()
                                                    }
                                                    is DownloadStatus.Success -> dlg.apply {
                                                        setTitle(R.string.FIRMWARE_UPDATE_FINISHED)
                                                        setMessage(context.getString(R.string.WAIT_FOR_THE_RADIO_TO_REBOOT))
                                                        getButton(AlertDialog.BUTTON_POSITIVE).isVisible = false
                                                        getButton(AlertDialog.BUTTON_NEGATIVE).text=context.getString(R.string.CLOSE)
                                                        invalidate()
                                                        receiver.stopOTA()
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

    override fun onCancel(dialog:DialogInterface) {
        super.onCancel(dialog)
        canceled=true
    }

    override fun onClick(v: View?) {}
}