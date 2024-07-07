package eu.ydiaeresis.trovalasonda

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.ydiaeresis.trovalasonda.databinding.SondetypeBinding
import kotlin.math.roundToInt


interface DialogCloseListener {
    fun handleDialogClose()
}

class SondeTypeDialog : DialogFragment(), View.OnClickListener  {
    private lateinit var binding: SondetypeBinding
    var type=0
    var freq=403.0
    var isCiapaSonde=false
    var dialogCloseListener: DialogCloseListener?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { it ->
            val inflater = requireActivity().layoutInflater

            binding=SondetypeBinding.inflate(inflater).apply {
                type.adapter = object : BaseAdapter() {
                    val adapter = ArrayAdapter.createFromResource(
                        this@SondeTypeDialog.requireContext(),
                        R.array.sonde_types,
                        R.layout.sonde_spinner_entry
                    )
                    override fun getDropDownView(position:Int,
                                                 convertView:View?,
                                                 parent:ViewGroup?):View? {
                        val mView=adapter.getDropDownView(position,convertView,parent!!)
                        if (position>=5 && !isCiapaSonde)
                            (mView as TextView).setTextColor(Color.GRAY)
                        return mView
                    }
                    override fun isEnabled(position:Int) : Boolean = position<5 || isCiapaSonde
                    override fun getCount():Int = adapter.count
                    override fun getItem(position:Int):Any = adapter.getItem(position) as Any
                    override fun getItemId(position:Int):Long = adapter.getItemId(position)
                    override fun getView(position:Int,convertView:View?,parent:ViewGroup?):View = adapter.getView(position,convertView,parent!!)
                }
                /*type.adapter = ArrayAdapter.createFromResource(
                    this@SondeTypeDialog.requireContext(),
                    R.array.sonde_types,
                    R.layout.sonde_spinner_entry
                )*/
                f100.apply { minValue = 4; maxValue = 4 }
                f10.apply { minValue = 0; maxValue = 0 }
                f1.apply { minValue = 0; maxValue = 5; value = freq.toInt() % 10 }
                var f = (1000 * (freq - freq.toInt())).roundToInt()
                listOf(f0001, f001, f01).forEach {
                    it.apply { minValue = 0;maxValue = 9;value = f % 10 }
                    f /= 10
                }
                root.invalidate()
            }
            binding.type.setSelection(type-1)
            return MaterialAlertDialogBuilder(it, R.style.MaterialAlertDialog_rounded)
                .setView(binding.root)
                .setPositiveButton("OK") { _, _ ->
                    binding.apply {
                        freq = 400 +
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