package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import eu.ydiaeresis.trovalasonda.databinding.SondetypeBinding

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
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater

            binding=SondetypeBinding.inflate(inflater).apply {

                val arrayAdapter: ArrayAdapter<Any?> = ArrayAdapter<Any?>(
                    requireContext(),
                    R.layout.spinner_style,
                    requireContext().resources.getStringArray(R.array.sonde_types)
                )
                arrayAdapter.setDropDownViewResource(R.layout.spinner_style)
                type.adapter = arrayAdapter
                f100.apply { textSize=120F; minValue = 4;maxValue = 4;value = (freq / 100F).toInt() }
                f10.apply { textSize=120F; minValue = 0;maxValue = 0;value = (freq / 10F).toInt() % 10 }
                f1.apply { textSize=120F; minValue = 0;maxValue = 6;value = freq.toInt() % 10 }
                var f = (1000 * (freq - freq.toInt())).toInt()
                listOf(R.id.f0001, R.id.f001, R.id.f01).forEach {
                    root.findViewById<NumberPicker>(it).apply { textSize=120F; minValue = 0;maxValue = 9;value = f % 10 }
                    f /= 10
                }
            }
            binding.type.setSelection(type-1)
            builder.setView(binding.root)
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
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
    override fun onClick(v: View?) {}
}