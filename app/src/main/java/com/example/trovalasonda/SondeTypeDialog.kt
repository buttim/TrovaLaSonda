package com.example.trovalasonda

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment

interface DialogCloseListener {
    fun handleDialogClose()
}

class SondeTypeDialog : DialogFragment(), View.OnClickListener  {
    var type:Int=0
    var freq:Float=403.0F
    var dialogCloseListener:DialogCloseListener?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater

            val view=inflater.inflate(R.layout.sondetype, null)
            val etFreq=view.findViewById<EditText>(R.id.freq)
            val spType=view.findViewById<Spinner>(R.id.type)
            builder.setView(view)
                .setPositiveButton("OK") { dialog, id ->
                    freq=etFreq.text.toString().toFloat()
                    type=spType.selectedItemPosition+1
                    dialogCloseListener?.handleDialogClose()
                }
                .setNegativeButton("Cancel") { dialog, id ->
                    getDialog()?.cancel()
                }

            etFreq.setText(freq.toString())
            spType.setSelection(type-1)
            etFreq.doOnTextChanged { text:CharSequence?, start, count, after ->
                try {
                    val freq=text.toString().toFloat()
                    if (freq<400 || freq>406)
                        etFreq.setError("Must be less than 406 and more than 400")
                }
                catch (e:Exception) {
                    etFreq.setError("Invalid format")
                }
            }
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
    override fun onClick(v: View?) {
        TODO("Not yet implemented")
    }
}