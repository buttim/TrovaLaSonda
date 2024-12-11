package eu.ydiaeresis.trovalasonda

//import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import eu.ydiaeresis.trovalasonda.databinding.ActivitySettingsBinding

interface FreqOffsetReceiver {
    fun freqOffset(offset:Int)
}

class SettingsActivity : AppCompatActivity(), FreqOffsetReceiver {
    private lateinit var binding: ActivitySettingsBinding
    private var sda=0
    private var scl=0
    private var rst=0
    private var lcd=0
    private var buz=0
    private var led=0
    private var bat=0
    private var batMin=0
    private var batMax=0
    private var batType=0
    private var rs41BW=0
    private var m20BW=0
    private var m10BW=0
    private var pilotBW=0
    private var dfmBW=0
    private var offset=0
    private var call=""
    private var nam=0
    private var currentOffset=0
    private var sumOfOffsets=0
    private var nOffsets=0

    //@SuppressLint("SetTextI18n")
    override fun freqOffset(offset:Int) {
        sumOfOffsets+=offset
        nOffsets++
        currentOffset=sumOfOffsets/nOffsets+this.offset
        binding.content.currentOffset.text = "(${currentOffset}Hz)"
        binding.content.currentOffset.invalidate()
        binding.content.tune.isEnabled=true
    }

    private fun setFields() {
        with (binding) {
            content.lcd.setSelection(lcd)
            content.call.setText(call)
            content.sda.setText(sda.toString())
            content.scl.setText(scl.toString())
            content.rst.setText(rst.toString())
            content.buz.setText(buz.toString())
            content.led.setText(led.toString())
            content.bat.setText(bat.toString())
            content.batMin.setText(batMin.toString())
            content.batMax.setText(batMax.toString())
            content.batType.setSelection(batType)
            content.rs41bw.setSelection(rs41BW)
            content.m20bw.setSelection(m20BW)
            content.m10bw.setSelection(m10BW)
            content.pilbw.setSelection(pilotBW)
            content.dfmbw.setSelection(dfmBW)
            content.nam.setSelection(nam)
            content.offset.setText(offset.toString())
        }
    }

    //@SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_settings)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sumOfOffsets=0
        nOffsets=0
        with (intent.extras!!) {
            sda =     getInt(TTGO.OLED_SDA)
            scl =     getInt(TTGO.OLED_SCL)
            rst =     getInt(TTGO.OLED_RST)
            lcd =     getInt(TTGO.LCD)
            buz =     getInt(TTGO.BUZ_PIN)
            led =     getInt(TTGO.LED_POUT)
            bat =     getInt(TTGO.BATTERY)
            batMin =  getInt(TTGO.VBATMIN)
            batMax =  getInt(TTGO.VBATMAX)
            batType = getInt(TTGO.VBATTYPE)
            rs41BW =  getInt(TTGO.RS41_RXBW)
            m20BW =   getInt(TTGO.M20_RXBW)
            m10BW =   getInt(TTGO.M10_RXBW)
            pilotBW = getInt(TTGO.PILOT_RXBW)
            dfmBW =   getInt(TTGO.DFM_RXBW)
            offset =  getInt(TTGO.FREQOFS)
            call =    getString(TTGO.MYCALL,"")
            nam =     getInt(TTGO.APRSNAME)
            val ver = getString("ver","")
            supportActionBar?.setTitle("Settings ($ver)")
        }

        FullscreenActivity.registerFreqOffsetReceiver(this)

        with (binding.content) {
            for (spinner in arrayOf(rs41bw,m10bw,m20bw,dfmbw,pilbw)) {
                (spinner.adapter as ArrayAdapter<*>).let {
                    it.setDropDownViewResource(R.layout.spinner_entry)
                    spinner.adapter = object : SpinnerAdapter by it {
                        override fun getDropDownView(
                            position: Int,
                            convertview: View?,
                            parent: ViewGroup
                        ): View {
                            val view=it.getDropDownView(position, convertview, parent) as TextView
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                view.setTextColor(applicationContext.getColor(R.color.spinner_text))
                                view.setBackgroundColor(
                                    applicationContext.getColor(
                                        if (position % 2 == 0)
                                            R.color.spinner_background1
                                        else
                                            R.color.spinner_background2
                                        )
                                )
                            }
                            return view
                        }
                    }
                }
            }

            call.filters+=arrayOf(InputFilter.AllCaps(),object:InputFilter{
                override fun filter(
                    source: CharSequence,
                    start: Int,
                    end: Int,
                    dest: Spanned?,
                    dstart: Int,
                    dend: Int
                ): CharSequence? {
                    if (source.contains('/')) {
                        Toast.makeText(applicationContext, "Invalid '/' character", Toast.LENGTH_SHORT).show()
                        return source.replace(Regex("/"),"")
                    }
                    return null
                }
            })

            tune.setOnClickListener {
                offset.setText(this@SettingsActivity.currentOffset.toString())
            }

            setFields()

            reset.setOnClickListener {
                lcd.setSelection(0)
                call.setText("MYCALL")
                sda.setText("21")
                scl.setText("22")
                rst.setText("16")
                buz.setText("0")
                led.setText("25")
                bat.setText("35")
                batMin.setText("2950")
                batMax.setText("4180")
                batType.setSelection(0)
                rs41bw.setSelection(1)
                m20bw.setSelection(9)
                m10bw.setSelection(9)
                pilbw.setSelection(7)
                nam.setSelection(0)
                offset.setText("0")
            }
        }
        binding.fab.setOnClickListener {
            val data=Intent().apply {
                with (binding) {
                    var t = content.lcd.selectedItemPosition
                    if (t != lcd) putExtra(TTGO.LCD, t)
                    val s = content.call.text.toString()
                    if (s != call) putExtra(TTGO.MYCALL, s)
                    t = content.sda.text.toString().toInt()
                    if (t != sda) putExtra(TTGO.OLED_SDA, t)
                    t = content.scl.text.toString().toInt()
                    if (t != scl) putExtra(TTGO.OLED_SCL, t)
                    t = content.rst.text.toString().toInt()
                    if (t != rst) putExtra(TTGO.OLED_RST, t)
                    t = content.buz.text.toString().toInt()
                    if (t != buz) putExtra(TTGO.BUZ_PIN, t)
                    t = content.led.text.toString().toInt()
                    if (t != led) putExtra(TTGO.LED_POUT, t)
                    t = content.bat.text.toString().toInt()
                    if (t != bat) putExtra(TTGO.BATTERY, t)
                    t = content.batMin.text.toString().toInt()
                    if (t != batMin) putExtra(TTGO.VBATMIN, t)
                    t = content.batMax.text.toString().toInt()
                    if (t != batMax) putExtra(TTGO.VBATMAX, t)
                    t = content.batType.selectedItemPosition
                    if (t != batType) putExtra(TTGO.VBATTYPE, t)
                    t = content.rs41bw.selectedItemPosition
                    if (t != rs41BW) putExtra(TTGO.RS41_RXBW, t)
                    t = content.m20bw.selectedItemPosition
                    if (t != m20BW) putExtra(TTGO.M20_RXBW, t)
                    t = content.m10bw.selectedItemPosition
                    if (t != m10BW) putExtra(TTGO.M10_RXBW, t)
                    t =content.pilbw.selectedItemPosition
                    if (t != pilotBW) putExtra(TTGO.PILOT_RXBW, t)
                    t = content.dfmbw.selectedItemPosition
                    if (t != dfmBW) putExtra(TTGO.DFM_RXBW, t)
                    t = content.nam.selectedItemPosition
                    if (t != nam) putExtra(TTGO.APRSNAME, t)
                    t = content.offset.text.toString().toIntOrNull()?:0;
                    if (t != offset) putExtra(TTGO.FREQOFS, t)
                }
            }
            setResult(Activity.RESULT_OK,data)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FullscreenActivity.unregisterFreqOffsetReceiver()
    }

    override fun onOptionsItemSelected(item: MenuItem) : Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}