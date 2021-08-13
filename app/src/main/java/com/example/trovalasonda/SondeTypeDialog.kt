package com.example.trovalasonda

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

interface DialogCloseListener {
    fun handleDialogClose()
}

class SondeTypeDialog : DialogFragment(), View.OnClickListener  {
    var type:Int=0
    var freq:Double=403.0
    var dialogCloseListener:DialogCloseListener?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val view=inflater.inflate(R.layout.sondetype, null)
            val spType=view.findViewById<Spinner>(R.id.type)

            val arrayAdapter: ArrayAdapter<Any?> = ArrayAdapter<Any?>(requireContext(), R.layout.spinner_style,requireContext().resources.getStringArray(R.array.sonde_types))
            arrayAdapter.setDropDownViewResource(R.layout.spinner_style)
            spType.adapter = arrayAdapter

            builder.setView(view)
                .setPositiveButton("OK") { _, _ ->
                    with (view) {
                        freq = findViewById<NumberPicker>(R.id.f100).value * 100 +
                                findViewById<NumberPicker>(R.id.f10).value * 10 +
                                findViewById<NumberPicker>(R.id.f1).value +
                                findViewById<NumberPicker>(R.id.f_1).value / 10.0 +
                                findViewById<NumberPicker>(R.id.f_01).value / 100.0 +
                                findViewById<NumberPicker>(R.id.f_001).value / 1000.0
                    }
                    type=spType.selectedItemPosition+1
                    dialogCloseListener?.handleDialogClose()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    dialog?.cancel()
                }

            with (view) {
                findViewById<NumberPicker>(R.id.f100).apply { textSize=120F; minValue = 4;maxValue = 4;value = (freq / 100F).toInt() }
                findViewById<NumberPicker>(R.id.f10).apply { textSize=120F; minValue = 0;maxValue = 0;value = (freq / 10F).toInt() % 10 }
                findViewById<NumberPicker>(R.id.f1).apply { textSize=120F; minValue = 0;maxValue = 6;value = freq.toInt() % 10 }
                var f = (1000 * (freq - freq.toInt())).toInt()
                listOf(R.id.f_001, R.id.f_01, R.id.f_1).forEach { it ->
                    findViewById<NumberPicker>(it).apply { textSize=120F; minValue = 0;maxValue = 9;value = f % 10 }
                    f /= 10
                }
            }

            spType.setSelection(type-1)
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
    override fun onClick(v: View?) {
    }
}