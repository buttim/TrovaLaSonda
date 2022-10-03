package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import eu.ydiaeresis.trovalasonda.databinding.SondetypeBinding
import io.ktor.http.content.*
import kotlin.math.roundToInt

interface DialogCloseListener {
    fun handleDialogClose()
}

class SondeTypeDialog : DialogFragment(), View.OnClickListener  {
    private lateinit var binding: SondetypeBinding
    var type=0
    var freq=403.0
    var dialogCloseListener: DialogCloseListener?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { it ->
            val inflater = requireActivity().layoutInflater

            binding=SondetypeBinding.inflate(inflater).apply {
                (type.adapter as ArrayAdapter<*>).setDropDownViewResource(R.layout.sonde_spinner_entry)
                type.adapter = ArrayAdapter.createFromResource(
                    this@SondeTypeDialog.requireContext(),
                    R.array.sonde_types,
                    R.layout.sonde_spinner_entry
                )

                f100.apply { minValue = 4;maxValue = 4;value = (freq / 100F).toInt() }
                f10.apply { minValue = 0;maxValue = 0;value = (freq / 10F).toInt() % 10 }
                f1.apply { minValue = 0;maxValue = 6;value = freq.toInt() % 10 }
                var f = (1000 * (freq - freq.toInt())).roundToInt()
                listOf(f0001, f001, f01).forEach {
                    it.apply { minValue = 0;maxValue = 9;value = f % 10 }
                    f /= 10
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    listOf(f100,f10,f1,f0001, f001, f01).forEach {
                        it.textSize=120F
                    }
            }
            binding.type.setSelection(type-1)
            return AlertDialog.Builder(it)
                .setView(binding.root)
                .setPositiveButton("OK") { _, _ ->
                    binding.apply {
                        freq = f100.value * 100 +
                                f10.value * 10 +
                                f1.value +
                                f01.value / 10.0 +
                                f001.value / 100.0 +
                                f0001.value / 1000.0
                    }
                    type=binding.type.selectedItemPosition+1
                    dialogCloseListener?.handleDialogClose()
                }
                .setNegativeButton("Cancel") { _, _ -> dialog?.cancel() }
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
    override fun onClick(v: View?) {}
}