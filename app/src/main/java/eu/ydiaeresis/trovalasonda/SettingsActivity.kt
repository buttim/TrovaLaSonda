package eu.ydiaeresis.trovalasonda

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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

    @SuppressLint("SetTextI18n")
    override fun freqOffset(offset:Int) {
        sumOfOffsets+=offset
        nOffsets++
        currentOffset=sumOfOffsets/nOffsets+this.offset
        binding.content.currentOffset.text = "(${currentOffset}Hz)"
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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_settings)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        with (intent.extras!!) {
            sda = getInt("oled_sda")
            scl = getInt("oled_scl")
            rst = getInt("oled_rst")
            lcd = getInt("lcd")
            buz = getInt("buz_pin")
            led = getInt("led_pout")
            bat = getInt("battery")
            batMin = getInt("vBatMin")
            batMax = getInt("vBatMax")
            batType = getInt("vBatType")
            rs41BW = getInt("rs41.rxbw")
            m20BW = getInt("m20.rxbw")
            m10BW = getInt("m10.rxbw")
            pilotBW = getInt("pilot.rxbw")
            dfmBW = getInt("dfm.rxbw")
            offset = getInt("freqofs")
            call = getString("myCall","")
            nam = getInt("aprsName")
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
                            view.setTextColor(applicationContext.getColor(R.color.spinner_text))
                            view.setBackgroundColor(
                                applicationContext.getColor(
                                    if (position % 2 == 0)
                                        R.color.spinner_background1
                                    else
                                        R.color.spinner_background2
                                    )
                            )
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
                    if (t != lcd) putExtra(LCD, t)
                    val s = content.call.text.toString()
                    if (s != call) putExtra(MYCALL, s)
                    t = content.sda.text.toString().toInt()
                    if (t != sda) putExtra(OLED_SDA, t)
                    t = content.scl.text.toString().toInt()
                    if (t != scl) putExtra(OLED_SCL, t)
                    t = content.rst.text.toString().toInt()
                    if (t != rst) putExtra(OLED_RST, t)
                    t = content.buz.text.toString().toInt()
                    if (t != buz) putExtra(BUZ_PIN, t)
                    t = content.led.text.toString().toInt()
                    if (t != led) putExtra(LED_POUT, t)
                    t = content.bat.text.toString().toInt()
                    if (t != bat) putExtra(BATTERY, t)
                    t = content.batMin.text.toString().toInt()
                    if (t != batMin) putExtra(VBATMIN, t)
                    t = content.batMax.text.toString().toInt()
                    if (t != batMax) putExtra(VBATMAX, t)
                    t = content.batType.selectedItemPosition
                    if (t != batType) putExtra(VBATTYPE, t)
                    t = content.rs41bw.selectedItemPosition
                    if (t != rs41BW) putExtra(RS41_RXBW, t)
                    t = content.m20bw.selectedItemPosition
                    if (t != m20BW) putExtra(M20_RXBW, t)
                    t = content.m10bw.selectedItemPosition
                    if (t != m10BW) putExtra(M10_RXBW, t)
                    t =content.pilbw.selectedItemPosition
                    if (t != pilotBW) putExtra(PILOT_RXBW, t)
                    t = content.dfmbw.selectedItemPosition
                    if (t != dfmBW) putExtra(DFM_RXBW, t)
                    t = content.nam.selectedItemPosition
                    if (t != nam) putExtra(APRSNAME, t)
                    t = content.offset.text.toString().toInt()
                    if (t != offset) putExtra(FREQOFS, t)
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

    companion object {
        const val LCD="lcd"
        const val OLED_SDA="oled_sda"
        const val OLED_SCL="oled_scl"
        const val OLED_RST="oled_rst"
        const val BUZ_PIN="buz_pin"
        const val LED_POUT="led_pout"
        const val BATTERY="battery"
        const val VBATMIN="vBatMin"
        const val VBATMAX="vBatMax"
        const val VBATTYPE="vBatType"
        const val RS41_RXBW="rs41.rxbw"
        const val M20_RXBW="m20.rxbw"
        const val M10_RXBW="m10.rxbw"
        const val PILOT_RXBW="pilot.rxbw"
        const val DFM_RXBW="dmf.rxbw"
        const val MYCALL="myCall"
        const val APRSNAME="aprsName"
        const val FREQOFS="freqofs"
    }
}