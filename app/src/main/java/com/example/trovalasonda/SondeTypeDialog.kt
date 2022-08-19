package com.example.trovalasonda

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.trovalasonda.databinding.SondetypeBinding

interface DialogCloseListener {
    fun handleDialogClose()
}

class SondeTypeDialog : DialogFragment(), View.OnClickListener  {
    private lateinit var binding: SondetypeBinding
    var type:Int=0
    var freq:Double=403.0
    var dialogCloseListener:DialogCloseListener?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { it ->
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater

            binding=SondetypeBinding.inflate(inflater)

            val arrayAdapter: ArrayAdapter<Any?> = ArrayAdapter<Any?>(requireContext(), R.layout.spinner_style,requireContext().resources.getStringArray(R.array.sonde_types))
            arrayAdapter.setDropDownViewResource(R.layout.spinner_style)
            binding.type.adapter = arrayAdapter

            builder.setView(binding.root)
                .setPositiveButton("OK") { _, _ ->
                    freq = binding.f100.value * 100 +
                            binding.f10.value * 10 +
                            binding.f1.value +
                            binding.f01.value / 10.0 +
                            binding.f001.value / 100.0 +
                            binding.f0001.value / 1000.0
                    type=binding.type.selectedItemPosition+1
                    dialogCloseListener?.handleDialogClose()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }

            binding.f100.apply { textSize=120F; minValue = 4;maxValue = 4;value = (freq / 100F).toInt() }
            binding.f10.apply { textSize=120F; minValue = 0;maxValue = 0;value = (freq / 10F).toInt() % 10 }
            binding.f1.apply { textSize=120F; minValue = 0;maxValue = 6;value = freq.toInt() % 10 }
            var f = (1000 * (freq - freq.toInt())).toInt()
            listOf(R.id.f0001, R.id.f001, R.id.f01).forEach {
                binding.root.findViewById<NumberPicker>(it).apply { textSize=120F; minValue = 0;maxValue = 9;value = f % 10 }
                f /= 10
            }

            binding.type.setSelection(type-1)
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
    override fun onClick(v: View?) {
    }
}